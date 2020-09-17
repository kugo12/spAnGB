pub struct PPU {
    palette_ram: [u8; 1024],
    vram: [u8; 96*1024],
    obj_attrib: [u8; 1024]
}

impl PPU {
    pub fn new() -> Self {
        Self {
            palette_ram: [0; 1024],
            vram: [0; 96*1024],
            obj_attrib: [0; 1024]
        }
    }

    pub fn tick(&mut self) {
        
    }
}
