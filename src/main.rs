use std::path::Path;


mod core;
use crate::core::spAnGB;

fn main() {
    let r = Path::new(&"irq_demo.gba");
    let b = Path::new(&"gba_bios.bin");

    let mut gba = spAnGB::new();
    
    gba.bus.load_bios(b);
    gba.bus.cartridge.insert(r);
    gba.run();
    //gba.bus.dump_ewram();
}
