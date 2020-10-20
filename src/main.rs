use std::path::Path;


mod core;
use crate::core::spAnGB;

fn main() {
    let r = Path::new(&"armwrestler-gba-fixed.gba");
    let b = Path::new(&"normatt.bin");

    let mut gba = spAnGB::new();
    
    gba.bus.load_bios(b);
    gba.bus.cartridge.insert(r);
    gba.run();
    //gba.bus.dump_ewram();
}
