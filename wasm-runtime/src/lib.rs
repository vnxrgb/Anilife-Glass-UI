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
