#include <iostream>
#include <sstream>
#include <string>
#include <thread>
#include <chrono>
#include <atomic>
#include <mutex>
#include <cmath>

#include "botcraft/Game/ManagersClient.hpp"
#include "botcraft/Game/World/World.hpp"
#include "botcraft/Game/World/Blockstate.hpp"
#include "botcraft/Game/Entities/LocalPlayer.hpp"
#include "botcraft/Utilities/Logger.hpp"

std::atomic<bool> should_quit(false);

// Current input state — updated by stdin thread, applied every physics tick
std::mutex input_mutex;
struct InputState {
    float forward = 0.0f;
    float left    = 0.0f;
    bool  jump    = false;
    bool  sneak   = false;
    bool  sprint  = false;
} current_inputs;

static std::string strip_prefix(const std::string& s, const std::string& prefix) {
    return s.substr(prefix.size());
}
static std::string escape_json(const std::string& s) {
    std::string out;
    for (char c : s) {
        if (c == '"')  out += "\\\"";
        else if (c == '\\') out += "\\\\";
        else out += c;
    }
    return out;
}

void applyInputs(Botcraft::ManagersClient* client) {
    auto player = client->GetLocalPlayer();
    if (!player) return;
    std::lock_guard<std::mutex> lock(input_mutex);
    player->SetInputsForward(current_inputs.forward);
    player->SetInputsLeft(current_inputs.left);
    player->SetInputsJump(current_inputs.jump);
    player->SetInputsSneak(current_inputs.sneak);
    player->SetInputsSprint(current_inputs.sprint);
}

void StdinReaderThread(Botcraft::ManagersClient* client) {
    std::string line;
    while (!should_quit) {
        if (!std::getline(std::cin, line)) {
            // stdin hit EOF or error — don't disconnect; wait and retry
            // This keeps the bot alive even if the pipe hasn't sent anything yet
            std::this_thread::sleep_for(std::chrono::milliseconds(200));
            // Reset cin error flags so we can try reading again
            std::cin.clear();
            continue;
        }
        if (line.empty()) continue;

        if (line == "quit" || line == "disconnect") {
            should_quit = true;
            client->SetShouldBeClosed(true);
            break;
        }
        else if (line.rfind("say ", 0) == 0) {
            client->SendChatMessage(strip_prefix(line, "say "));
        }
        else if (line.rfind("cmd ", 0) == 0) {
            client->SendChatCommand(strip_prefix(line, "cmd "));
        }
        // Movement: "input <axis> <value>"
        // e.g. "input forward 1", "input forward 0", "input jump 1"
        else if (line.rfind("input ", 0) == 0) {
            std::string rest = strip_prefix(line, "input ");
            std::istringstream ss(rest);
            std::string axis;
            float val = 0.0f;
            ss >> axis >> val;

            {
                std::lock_guard<std::mutex> lock(input_mutex);
                if      (axis == "forward") current_inputs.forward = val;
                else if (axis == "back")    current_inputs.forward = -val;
                else if (axis == "left")    current_inputs.left    = val;
                else if (axis == "right")   current_inputs.left    = -val;
                else if (axis == "jump")    current_inputs.jump    = (val > 0.0f);
                else if (axis == "sneak")   current_inputs.sneak   = (val > 0.0f);
                else if (axis == "sprint")  current_inputs.sprint  = (val > 0.0f);
                else if (axis == "stop") {
                    current_inputs = {};
                }
                // Auto-sprint when moving forward
                if (axis == "forward" && val > 0.0f) {
                    current_inputs.sprint = true;
                }
                if (current_inputs.forward <= 0.0f) {
                    current_inputs.sprint = false;
                }
            }
            applyInputs(client);
        }
    }
    // Clear inputs on exit
    { std::lock_guard<std::mutex> lock(input_mutex); current_inputs = {}; }
    applyInputs(client);
}

void StatusThread(Botcraft::ManagersClient* client) {
    int tick = 0;
    while (!should_quit) {
        std::this_thread::sleep_for(std::chrono::milliseconds(250));
        if (should_quit) break;

        // Stop if the client itself decided to close (server kicked us, etc.)
        if (client->GetShouldBeClosed()) {
            should_quit = true;
            break;
        }

        // Re-apply inputs every tick so physics picks them up
        try { applyInputs(client); } catch (...) {}

        try {
            auto player = client->GetLocalPlayer();
            if (!player) continue;

            double px = player->GetX();
            double py = player->GetY();
            double pz = player->GetZ();
            float  yaw      = player->GetYaw();
            float  pitch    = player->GetPitch();
            float  health   = player->GetHealth();
            int    food     = player->GetFood();
            bool   on_ground = player->GetOnGround();

            // Status JSON — every tick
            std::cout
                << "{\"type\":\"status\","
                << "\"x\":"        << px       << ","
                << "\"y\":"        << py       << ","
                << "\"z\":"        << pz       << ","
                << "\"yaw\":"      << yaw      << ","
                << "\"pitch\":"    << pitch    << ","
                << "\"health\":"   << health   << ","
                << "\"food\":"     << food     << ","
                << "\"on_ground\":" << (on_ground ? "true" : "false")
                << "}\n";

            // Block map — every 4 ticks (1 second) to reduce CPU
            if (tick % 4 == 0) {
                auto world = client->GetWorld();
                if (world) {
                    int bx = (int)std::floor(px);
                    int by = (int)std::floor(py);
                    int bz = (int)std::floor(pz);
                    const int R = 16;

                    std::cout << "{\"type\":\"blocks\","
                              << "\"cx\":" << bx << ","
                              << "\"cy\":" << by << ","
                              << "\"cz\":" << bz << ","
                              << "\"r\":"  << R  << ","
                              << "\"data\":[";

                    for (int dz = -R; dz <= R; dz++) {
                        if (dz != -R) std::cout << ',';
                        std::cout << '[';
                        for (int dx = -R; dx <= R; dx++) {
                            if (dx != -R) std::cout << ',';
                            std::string name = "air";
                            for (int dy = 0; dy <= 5 && name == "air"; dy++) {
                                Botcraft::Position pos(bx + dx, by - dy, bz + dz);
                                const Botcraft::Blockstate* b = world->GetBlock(pos);
                                if (b && !b->IsAir()) {
                                    name = b->GetName();
                                    if (name.rfind("minecraft:", 0) == 0) name = name.substr(10);
                                }
                            }
                            std::cout << '"' << escape_json(name) << '"';
                        }
                        std::cout << ']';
                    }
                    std::cout << "]}\n";
                }
            }

            std::cout.flush();
            tick++;
        }
        catch (const std::exception& e) {
            std::cout << "[StatusThread error] " << e.what() << "\n";
            std::cout.flush();
        }
        catch (...) {
            std::cout << "[StatusThread unknown error]\n";
            std::cout.flush();
        }
    }
}

int main(int argc, char* argv[]) {
    std::string address = "127.0.0.1:25565";
    std::string login   = "BotcraftBot";
    for (int i = 1; i < argc; i++) {
        std::string arg = argv[i];
        if (arg == "--address" && i + 1 < argc) address = argv[++i];
        else if (arg == "--login" && i + 1 < argc) login = argv[++i];
    }
    try {
        Botcraft::Logger::GetInstance().SetLogLevel(Botcraft::LogLevel::Info);
        Botcraft::Logger::GetInstance().SetFilename("");
        Botcraft::Logger::GetInstance().RegisterThread("main");

        Botcraft::ManagersClient client;
        client.SetAutoRespawn(true);

        std::thread stdin_th(StdinReaderThread, &client);
        stdin_th.detach();

        client.Connect(address, login);

        std::thread status_th(StatusThread, &client);

        while (!should_quit && !client.GetShouldBeClosed()) {
            std::this_thread::sleep_for(std::chrono::milliseconds(50));
        }
        should_quit = true;
        if (status_th.joinable()) status_th.join();
        client.Disconnect();
        return 0;
    }
    catch (std::exception& e) {
        std::cerr << "[FATAL] " << e.what() << "\n";
        return 1;
    }
}
