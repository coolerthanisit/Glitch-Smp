import os
import subprocess
import threading
import time
import uuid
import json
from collections import deque
from flask import Flask, render_template, request, jsonify
from flask_socketio import SocketIO, emit

app = Flask(__name__)
app.config['SECRET_KEY'] = 'botcraft-ui-secret'
socketio = SocketIO(app, cors_allowed_origins="*", async_mode='threading')

ROOT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
BIN_DIR = os.path.join(ROOT_DIR, 'bin')
BOT_BINARY = os.path.join(BIN_DIR, 'controlbot')

bots = {}
bots_lock = threading.Lock()


class BotProcess:
    def __init__(self, bot_id, address, login, bot_type):
        self.bot_id = bot_id
        self.address = address
        self.login = login
        self.bot_type = bot_type
        self.process = None
        self.logs = deque(maxlen=500)
        self.status = 'connecting'
        self.connected_at = time.strftime('%H:%M:%S')
        self.player_status = {}   # latest {x,y,z,yaw,pitch,health,food,...}
        self.block_map = {}       # latest {cx,cy,cz,r,data}
        self._log_thread = None

    def start(self):
        binary = BOT_BINARY if self.bot_type == 'control' else os.path.join(BIN_DIR, self.bot_type)
        self.log_file_path = f'/tmp/botcraft_{self.bot_id}.log'
        env = os.environ.copy()
        env['LD_LIBRARY_PATH'] = BIN_DIR + ':' + env.get('LD_LIBRARY_PATH', '')

        self.process = subprocess.Popen(
            [binary, '--address', self.address, '--login', self.login],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1,
            env=env,
            cwd=ROOT_DIR
        )
        self._log_thread = threading.Thread(target=self._read_output, daemon=True)
        self._log_thread.start()

    def _read_output(self):
        log_file = open(self.log_file_path, 'w') if hasattr(self, 'log_file_path') else None
        try:
            for raw in iter(self.process.stdout.readline, ''):
                line = raw.rstrip()
                if not line:
                    continue

                # Write everything to log file for diagnosis
                if log_file:
                    log_file.write(line + '\n')
                    log_file.flush()

                # Try to parse JSON status/block lines
                if line.startswith('{'):
                    try:
                        obj = json.loads(line)
                        t = obj.get('type')
                        if t == 'status':
                            self.player_status = obj
                            if self.status == 'connecting':
                                self.status = 'connected'
                            socketio.emit('bot_status_data', {
                                'bot_id': self.bot_id,
                                'player': obj
                            })
                            continue
                        elif t == 'blocks':
                            self.block_map = obj
                            socketio.emit('bot_blocks', {
                                'bot_id': self.bot_id,
                                'blocks': obj
                            })
                            continue
                    except Exception:
                        pass

                # Regular log line
                self.logs.append(line)
                if 'Spawned' in line or 'spawned' in line:
                    self.status = 'connected'
                elif 'Disconnect' in line or 'disconnect' in line or 'kicked' in line.lower():
                    self.status = 'disconnected'

                socketio.emit('bot_log', {
                    'bot_id': self.bot_id,
                    'line': line,
                    'status': self.status
                })
        except Exception as e:
            if log_file:
                log_file.write(f'[read_output exception] {e}\n')
        finally:
            if log_file:
                log_file.close()

        self.status = 'disconnected'
        socketio.emit('bot_status', {'bot_id': self.bot_id, 'status': 'disconnected'})

    def send_command(self, cmd):
        if self.process and self.process.stdin:
            try:
                self.process.stdin.write(cmd + '\n')
                self.process.stdin.flush()
                return True
            except Exception:
                return False
        return False

    def disconnect(self):
        self.send_command('quit')
        if self.process:
            try:
                self.process.wait(timeout=3)
            except subprocess.TimeoutExpired:
                self.process.kill()
        self.status = 'disconnected'

    def to_dict(self):
        return {
            'bot_id': self.bot_id,
            'address': self.address,
            'login': self.login,
            'bot_type': self.bot_type,
            'status': self.status,
            'connected_at': self.connected_at,
            'logs': list(self.logs)[-100:],
            'player_status': self.player_status,
            'block_map': self.block_map,
        }


@app.route('/')
def index():
    return render_template('index.html')


@app.route('/api/bots', methods=['GET'])
def get_bots():
    with bots_lock:
        return jsonify([b.to_dict() for b in bots.values()])


@app.route('/api/bots/connect', methods=['POST'])
def connect_bot():
    data = request.json
    address = data.get('address', '127.0.0.1') + ':' + str(data.get('port', '25565'))
    login = data.get('login', 'BotcraftBot')
    bot_type = data.get('bot_type', 'control')

    if bot_type == 'control' and not os.path.exists(BOT_BINARY):
        return jsonify({'error': 'controlbot binary not found. Please build it first.'}), 400

    bot_id = str(uuid.uuid4())[:8]
    bot = BotProcess(bot_id, address, login, bot_type)
    with bots_lock:
        bots[bot_id] = bot
    try:
        bot.start()
        return jsonify({'bot_id': bot_id, 'status': 'connecting'})
    except Exception as e:
        with bots_lock:
            del bots[bot_id]
        return jsonify({'error': str(e)}), 500


@app.route('/api/bots/<bot_id>/disconnect', methods=['POST'])
def disconnect_bot(bot_id):
    with bots_lock:
        bot = bots.get(bot_id)
    if not bot:
        return jsonify({'error': 'Bot not found'}), 404
    bot.disconnect()
    with bots_lock:
        bots.pop(bot_id, None)
    return jsonify({'status': 'disconnected'})


@app.route('/api/bots/<bot_id>/chat', methods=['POST'])
def bot_chat(bot_id):
    with bots_lock:
        bot = bots.get(bot_id)
    if not bot:
        return jsonify({'error': 'Bot not found'}), 404
    msg = request.json.get('message', '')
    if not msg:
        return jsonify({'error': 'No message'}), 400
    success = bot.send_command(f'say {msg}')
    return jsonify({'success': success})


@app.route('/api/bots/<bot_id>/command', methods=['POST'])
def bot_command(bot_id):
    with bots_lock:
        bot = bots.get(bot_id)
    if not bot:
        return jsonify({'error': 'Bot not found'}), 404
    cmd = request.json.get('command', '')
    if not cmd:
        return jsonify({'error': 'No command'}), 400
    success = bot.send_command(f'cmd {cmd}')
    return jsonify({'success': success})


@app.route('/api/bots/<bot_id>/move', methods=['POST'])
def bot_move(bot_id):
    with bots_lock:
        bot = bots.get(bot_id)
    if not bot:
        return jsonify({'error': 'Bot not found'}), 404
    data = request.json or {}
    action = data.get('action', 'stop')    # "start" or "stop"
    direction = data.get('direction', '')  # "forward","back","left","right","jump","sneak","sprint"

    if action == 'start' and direction:
        success = bot.send_command(f'input {direction} 1')
    else:
        # stop the specific axis or all if no direction given
        if direction in ('forward', 'back'):
            success = bot.send_command('input forward 0')
        elif direction in ('left', 'right'):
            success = bot.send_command('input left 0')
        elif direction == 'jump':
            success = bot.send_command('input jump 0')
        elif direction == 'sneak':
            success = bot.send_command('input sneak 0')
        elif direction == 'sprint':
            success = bot.send_command('input sprint 0')
        else:
            success = bot.send_command('input stop 0')
    return jsonify({'success': success})


@app.route('/api/bots/<bot_id>/status', methods=['GET'])
def bot_status(bot_id):
    with bots_lock:
        bot = bots.get(bot_id)
    if not bot:
        return jsonify({'error': 'Bot not found'}), 404
    return jsonify({
        'player': bot.player_status,
        'blocks': bot.block_map,
    })


@app.route('/api/bots/<bot_id>/logs', methods=['GET'])
def bot_logs(bot_id):
    with bots_lock:
        bot = bots.get(bot_id)
    if not bot:
        return jsonify({'error': 'Bot not found'}), 404
    return jsonify({'logs': list(bot.logs)})


@socketio.on('connect')
def handle_connect():
    with bots_lock:
        bot_list = [b.to_dict() for b in bots.values()]
    emit('bot_list', bot_list)


if __name__ == '__main__':
    socketio.run(app, host='0.0.0.0', port=5000, debug=False)
