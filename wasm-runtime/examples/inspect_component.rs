//! Offline compatibility probe. No guest HTTP broker and no inherited WASI capabilities.
use anilumos_wasm_runtime::adapter::inspect;
fn main() -> anyhow::Result<()> {
    let path = std::env::args_os().nth(1).ok_or_else(|| anyhow::anyhow!("missing component path"))?;
    let bytes = std::fs::read(path)?;
    let metadata = inspect(&bytes, true)?;
    println!("metadata ABI linked; provider name length={}, declared hosts={}", metadata.name.len(), metadata.hosts.len());
    Ok(())
}
