//! Experimental runtime probe. No WASI or guest host imports are linked yet.
use anyhow::{Result, bail};
use wasmtime::{Config, Engine, Store, StoreLimits, StoreLimitsBuilder};
use wasmtime::component::{Component, Linker};

const LIMIT: usize = 64 * 1024 * 1024;
struct State { limits: StoreLimits }

fn engine() -> Result<Engine> {
    let mut config = Config::new();
    config.wasm_component_model(true).consume_fuel(true);
    Ok(Engine::new(&config)?)
}

/// Compile and instantiate only a self-contained component. No file/network imports.
pub fn probe(bytes: &[u8]) -> Result<()> {
    if bytes.len() > LIMIT || !bytes.starts_with(b"\0asm\x0d\0\x01\0") {
        bail!("invalid component");
    }
    let engine = engine()?;
    let component = Component::new(&engine, bytes)?;
    let linker = Linker::<State>::new(&engine);
    let mut store = Store::new(&engine, State {
        limits: StoreLimitsBuilder::new().memory_size(LIMIT).instances(8).memories(4).tables(8).build(),
    });
    store.limiter(|state| &mut state.limits);
    store.set_fuel(100_000_000)?;
    linker.instantiate(&mut store, &component)?;
    Ok(())
}

#[cfg(target_os = "android")]
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_anilili_data_wasm_NativeSandbox_probe(
    env: jni::JNIEnv,
    _class: jni::objects::JClass,
    bytes: jni::objects::JByteArray,
) -> jni::sys::jboolean {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let size = env.get_array_length(&bytes).ok()?;
        if size < 8 || size as usize > LIMIT { return None; }
        let bytes = env.convert_byte_array(bytes).ok()?;
        probe(&bytes).ok()
    }));
    if matches!(result, Ok(Some(()))) { 1 } else { 0 }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn empty_component_is_valid() {
        assert!(probe(b"\0asm\x0d\0\x01\0").is_ok());
    }
    #[test]
    fn rejects_core_and_malformed_components() {
        assert!(probe(b"\0asm\x01\0\0\0").is_err());
        assert!(probe(b"\0asm\x0d\0\x01\0\xff").is_err());
    }
}

pub mod adapter;

// Android-only callback bridge. The caller must be the isolated service.
#[cfg(target_os = "android")]
mod android_bridge {
    use super::adapter::{HttpBroker, HttpRequest, HttpReply};
    use jni::{JNIEnv, JavaVM, objects::{GlobalRef, JObject, JValue, JString, JByteArray, JClass}};

    struct BinderBroker { vm: JavaVM, binder: GlobalRef, bridge: GlobalRef }
    impl HttpBroker for BinderBroker {
        fn fetch(&mut self, req: HttpRequest) -> Result<HttpReply, String> { self.request(req, None) }
        fn browser_fetch(&mut self, origin: String, req: HttpRequest) -> Result<HttpReply, String> { self.request(req, Some(origin)) }
    }
    impl BinderBroker {
        fn request(&mut self, req: HttpRequest, origin: Option<String>) -> Result<HttpReply, String> {
            let mut env = self.vm.attach_current_thread().map_err(|e| e.to_string())?;
            let mut value = serde_json::to_value(&req).map_err(|e| e.to_string())?;
            if let Some(origin) = origin { value["browser_origin"] = serde_json::Value::String(origin); }
            let input = serde_json::to_string(&value).map_err(|e| e.to_string())?;
            if input.len() > 128 * 1024 { return Err("request too large".into()); }
            let input = env.new_string(input).map_err(|e| e.to_string())?;
            let result = env.call_static_method(
                <&JClass>::from(self.bridge.as_obj()),
                "fetchRemote",
                "(Landroid/os/IBinder;Ljava/lang/String;)[B",
                &[JValue::Object(self.binder.as_obj()), JValue::Object(input.as_ref())],
            ).and_then(|v| v.l()).map_err(|e| e.to_string())?;
            let output = env.convert_byte_array(JByteArray::from(result)).map_err(|e| e.to_string())?;
            super::adapter::decode_broker_reply(&output)
        }
    }

    #[unsafe(no_mangle)]
    pub extern "system" fn Java_com_anilili_data_wasm_NativeSandbox_resolve(
        mut env: JNIEnv, _: JClass, module: JByteArray, community: jni::sys::jboolean,
        query: JString, binder: JObject, bridge: JClass,
    ) -> jni::sys::jstring {
        let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| -> Option<String> {
            let n = env.get_array_length(&module).ok()?;
            if n < 8 || n as usize > super::LIMIT { return None; }
            let bytes = env.convert_byte_array(module).ok()?;
            let query: String = env.get_string(&query).ok()?.into();
            if query.len() > 32768 { return None; }
            let query = serde_json::from_str(&query).ok()?;
            let broker = BinderBroker { vm: env.get_java_vm().ok()?, binder: env.new_global_ref(binder).ok()?, bridge: env.new_global_ref(bridge).ok()? };
            let servers = super::adapter::resolve(&bytes, community != 0, query, Box::new(broker)).ok()?;
            let output = serde_json::to_string(&servers).ok()?;
            if output.len() > 200000 { return None; }
            Some(output)
        }));
        let output = result.ok().flatten().unwrap_or_default();
        env.new_string(output).map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut())
    }
}
