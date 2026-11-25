//! Typed ABI bindings for the two structurally distinct provider worlds.
//! All host operations deny by default until the authenticated IPC broker is wired.
use anyhow::Result;
use wasmtime::{Engine, Store, StoreLimitsBuilder};
use wasmtime::component::{Component, Linker};

mod modern {
    wasmtime::component::bindgen!({ path: "wit/desktop", world: "provider" });
}
mod legacy {
    wasmtime::component::bindgen!({ path: "wit/community", world: "provider" });
}

#[derive(Debug, Clone, PartialEq)]
pub struct ProviderMetadata {
    pub name: String,
    pub id: Option<String>,
    pub version: Option<String>,
    pub hosts: Vec<String>,
}

struct DeniedHost {
    limits: wasmtime::StoreLimits,
    wasi: wasmtime_wasi::WasiCtx,
    table: wasmtime::component::ResourceTable,
}

impl wasmtime_wasi::WasiView for DeniedHost {
    fn ctx(&mut self) -> wasmtime_wasi::WasiCtxView<'_> {
        wasmtime_wasi::WasiCtxView { ctx: &mut self.wasi, table: &mut self.table }
    }
}

impl modern::anilili::provider::types::Host for DeniedHost {}
impl modern::anilili::provider::host::Host for DeniedHost {
    fn http(&mut self, _: modern::anilili::provider::host::Request) -> std::result::Result<modern::anilili::provider::host::Response, String> {
        Err("network broker unavailable".into())
    }
    fn browser_fetch(&mut self, _: String, _: modern::anilili::provider::host::Request) -> std::result::Result<modern::anilili::provider::host::Response, String> {
        Err("browser access disabled".into())
    }
    fn log(&mut self, _: String) {}
    fn cache_get(&mut self, _: String) -> Option<String> { None }
    fn cache_set(&mut self, _: String, _: String, _: u32) {}
}

impl legacy::anilili::provider::types::Host for DeniedHost {}
impl legacy::anilili::provider::host::Host for DeniedHost {
    fn http(&mut self, _: legacy::anilili::provider::types::Request) -> std::result::Result<legacy::anilili::provider::types::Response, String> {
        Err("network broker unavailable".into())
    }
    fn browser_fetch(&mut self, _: String, _: legacy::anilili::provider::types::Request) -> std::result::Result<legacy::anilili::provider::types::Response, String> {
        Err("browser access disabled".into())
    }
    fn log(&mut self, _: String) {}
    fn cache_get(&mut self, _: String) -> Option<String> { None }
    fn cache_set(&mut self, _: String, _: String, _: u32) {}
}

fn store(engine: &Engine) -> Result<Store<DeniedHost>> {
    let mut store = Store::new(engine, DeniedHost {
        wasi: wasmtime_wasi::WasiCtx::builder().build(),
        table: wasmtime::component::ResourceTable::new(),
        limits: StoreLimitsBuilder::new().memory_size(64 << 20).instances(32).memories(4).tables(16).build(),
    });
    store.limiter(|s| &mut s.limits);
    store.set_fuel(100_000_000)?;
    Ok(store)
}

/// The caller chooses the ABI from reviewed metadata, never by executing unknown variants.
pub fn inspect(bytes: &[u8], community: bool) -> Result<ProviderMetadata> {
    anyhow::ensure!(bytes.len() <= 64 << 20 && bytes.starts_with(b"\0asm\x0d\0\x01\0"), "invalid component");
    let engine = super::engine()?;
    let component = Component::new(&engine, bytes)?;
    let mut store = store(&engine)?;
    let mut linker = Linker::new(&engine);
    wasmtime_wasi::p2::add_to_linker_sync(&mut linker)?;
    let result = if community {
        legacy::Provider::add_to_linker::<_, wasmtime::component::HasSelf<_>>(&mut linker, |s| s)?;
        let instance = legacy::Provider::instantiate(&mut store, &component, &linker)?;
        let info = instance.call_info(&mut store)?;
        ProviderMetadata { name: info.name, id: None, version: None, hosts: info.hosts }
    } else {
        modern::Provider::add_to_linker::<_, wasmtime::component::HasSelf<_>>(&mut linker, |s| s)?;
        let instance = modern::Provider::instantiate(&mut store, &component, &linker)?;
        let info = instance.call_info(&mut store)?;
        ProviderMetadata { name: info.name, id: Some(info.id), version: Some(info.version), hosts: info.hosts }
    };
    anyhow::ensure!(result.name.len() <= 128 && result.hosts.len() <= 64 && result.hosts.iter().all(|h| h.len() <= 253), "oversized metadata");
    Ok(result)
}
