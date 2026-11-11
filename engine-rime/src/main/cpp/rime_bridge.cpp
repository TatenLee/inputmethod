#include <jni.h>
#include <rime_api.h>
#include <string>
#include <stdexcept>
#include <iterator>
#include <utf8.h>

// Native 生命周期只由 Java 单线程队列访问。
namespace {
RimeApi* api = nullptr;
RimeSessionId session = 0;
std::string shared_dir, user_dir, schema;

std::string from_java(JNIEnv* env, jstring text) {
    const jchar* chars = env->GetStringChars(text, nullptr);
    if (!chars) throw std::runtime_error("Cannot read Java string");
    std::u16string value(reinterpret_cast<const char16_t*>(chars), env->GetStringLength(text));
    env->ReleaseStringChars(text, chars);
    std::string result;
    utf8::utf16to8(value.begin(), value.end(), std::back_inserter(result));
    return result;
}
jstring java_string(JNIEnv* env, const std::string& text) {
    std::u16string value;
    utf8::utf8to16(text.begin(), text.end(), std::back_inserter(value));
    return env->NewString(reinterpret_cast<const jchar*>(value.data()), static_cast<jsize>(value.size()));
}
std::string quote(const char* value) {
    std::string result = "\"";
    if (value) for (const unsigned char c : std::string(value)) {
        if (c == '\\' || c == '"') { result += '\\'; result += static_cast<char>(c); }
        else if (c < 32) {
            static const char* hex = "0123456789abcdef";
            result += "\\u00"; result += hex[c >> 4]; result += hex[c & 15];
        } else result += static_cast<char>(c);
    }
    return result + "\"";
}
void fail(JNIEnv* env, const char* message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message);
}
std::string snapshot(bool handled) {
    RimeCommit commit{};
    RIME_STRUCT_INIT(RimeCommit, commit);
    std::string text;
    if (api->get_commit(session, &commit)) {
        if (commit.text) text = commit.text;
        api->free_commit(&commit);
    }
    std::string json = "{\"handled\":" + std::string(handled ? "true" : "false") + ",\"commit\":" + quote(text.c_str());
    RimeContext ctx{};
    RIME_STRUCT_INIT(RimeContext, ctx);
    if (api->get_context(session, &ctx)) {
        json += ",\"preedit\":" + quote(ctx.composition.preedit);
        json += ",\"lastPage\":" + std::string(ctx.menu.is_last_page ? "true" : "false");
        json += ",\"page\":" + std::to_string(ctx.menu.page_no) + ",\"candidates\":[";
        for (int i = 0; i < ctx.menu.num_candidates; ++i) {
            if (i) json += ',';
            json += quote(ctx.menu.candidates[i].text);
        }
        json += "]}";
        api->free_context(&ctx);
    } else json += ",\"preedit\":\"\",\"lastPage\":true,\"page\":0,\"candidates\":[]}";
    return json;
}
}

extern "C" JNIEXPORT void JNICALL
Java_indi_tatenlee_inputmethod_engine_RimeNative_initialize(JNIEnv* env, jclass, jstring shared, jstring user) {
    try {
        if (session) return;
        shared_dir = from_java(env, shared); user_dir = from_java(env, user);
        api = rime_get_api();
        RimeTraits traits{};
        RIME_STRUCT_INIT(RimeTraits, traits);
        traits.shared_data_dir = shared_dir.c_str();
        traits.user_data_dir = user_dir.c_str();
        traits.distribution_name = "Jianxin";
        traits.distribution_code_name = "jianxin";
        traits.distribution_version = "0.1.0";
        traits.app_name = "rime.jianxin";
        traits.min_log_level = 3;
        api->setup(&traits);
        api->initialize(&traits);
        if (api->start_maintenance(false)) api->join_maintenance_thread();
        session = api->create_session();
        if (!session) throw std::runtime_error("Cannot create Rime session");
    } catch (const std::exception& e) { fail(env, e.what()); }
}

extern "C" JNIEXPORT jstring JNICALL
Java_indi_tatenlee_inputmethod_engine_RimeNative_command(JNIEnv* env, jclass, jint type, jint value,
                                                       jboolean nine_key, jboolean private_mode) {
    try {
        if (!session) throw std::runtime_error("Rime not initialized");
        std::string wanted = nine_key ? "jianxin_t9" : "jianxin_pinyin";
        if (private_mode) wanted += "_private";
        if (schema != wanted) {
            api->clear_composition(session);
            if (!api->select_schema(session, wanted.c_str())) throw std::runtime_error("Missing Rime schema");
            schema = wanted;
        }
        api->set_option(session, "ascii_mode", false);
        bool handled = true;
        if (type == 1) {
            if (value == 0xff0d) handled = api->commit_composition(session);
            else handled = api->process_key(session, value, 0);
        }
        else if (type == 2) api->select_candidate_on_current_page(session, static_cast<size_t>(value));
        else if (type == 3 || type == 4) api->clear_composition(session);
        else if (type == 5) api->commit_composition(session);
        return java_string(env, snapshot(handled));
    } catch (const std::exception& e) { fail(env, e.what()); return nullptr; }
}

extern "C" JNIEXPORT void JNICALL
Java_indi_tatenlee_inputmethod_engine_RimeNative_shutdown(JNIEnv*, jclass) {
    if (api) {
        if (session) api->destroy_session(session);
        session = 0;
        api->finalize();
        api = nullptr;
        schema.clear();
    }
}
