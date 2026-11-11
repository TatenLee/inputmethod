#include <rime_api.h>
#include <filesystem>
#include <iostream>
#include <string>
#include <vector>

// 用与 APK 相同的 CMake、RIME 提交和方案，实际部署词库并回放输入。
int main(int argc, char** argv) {
    if (argc != 3) { std::cerr << "Usage: engine_check shared-data temporary-user-data\n"; return 2; }
    std::filesystem::create_directories(argv[2]);
    auto api = rime_get_api();
    RIME_STRUCT(RimeTraits, traits);
    traits.shared_data_dir = argv[1]; traits.user_data_dir = argv[2];
    traits.app_name = "rime.jianxin.test";
    api->setup(&traits); api->initialize(&traits);
    if (api->start_maintenance(true)) api->join_maintenance_thread();
    auto session = api->create_session();
    int failures = 0;
    struct Case { const char* schema; const char* keys; const char* expected; };
    std::vector<Case> cases = {
        {"jianxin_pinyin", "nihao", "你好"},
        {"jianxin_pinyin", "zhongguo", "中国"},
        {"jianxin_pinyin", "shurufa", "输入法"},
        {"jianxin_pinyin", "nh", "你好"},
        {"jianxin_t9", "64426", "你好"},
        {"jianxin_t9", "94664486", "中国"},
    };
    for (const auto& item : cases) {
        api->clear_composition(session);
        if (!api->select_schema(session, item.schema)) { ++failures; std::cerr << "Missing " << item.schema << '\n'; continue; }
        for (char key : std::string(item.keys)) api->process_key(session, key, 0);
        bool found = false;
        for (int page = 0; page < 5 && !found; ++page) {
            RIME_STRUCT(RimeContext, ctx);
            if (!api->get_context(session, &ctx)) break;
            int selected = -1;
            for (int i = 0; i < ctx.menu.num_candidates; ++i)
                if (std::string(ctx.menu.candidates[i].text) == item.expected) selected = i;
            bool last = ctx.menu.is_last_page;
            api->free_context(&ctx);
            if (selected >= 0) {
                api->select_candidate_on_current_page(session, selected);
                RIME_STRUCT(RimeCommit, commit);
                if (api->get_commit(session, &commit)) {
                    found = std::string(commit.text) == item.expected;
                    api->free_commit(&commit);
                }
                break;
            }
            if (last) break;
            api->process_key(session, 0xff56, 0);
        }
        std::cout << (found ? "PASS " : "FAIL ") << item.schema << " " << item.keys << " → " << item.expected << '\n';
        if (!found) ++failures;
    }
    api->clear_composition(session);
    api->process_key(session, 'n', 0);
    api->process_key(session, 0xff08, 0);
    RIME_STRUCT(RimeContext, cleared);
    if (api->get_context(session, &cleared)) {
        if (cleared.composition.length != 0) ++failures;
        api->free_context(&cleared);
    }
    api->destroy_session(session); api->finalize();
    return failures ? 1 : 0;
}
