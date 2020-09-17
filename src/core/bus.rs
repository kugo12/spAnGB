use crate::core::PPU;

pub struct Bus {
    wram: Box<[u8; 256*1024]>,  // on-board wram
    iwram: [u8; 32*1024],  // internal wram (on-chip)

    ppu: PPU,
}

impl Bus {
    pub fn new() -> Self {
        Self {
            wram: Box::new([0; 256*1024]),
            iwram: [0; 32*1024],

            ppu: PPU::new()
        }
    }

    pub fn tick(&mut self) {
        self.ppu.tick();
    }

    pub fn read8(&mut self, addr: u32) -> u8 {
        0
    }

    pub fn read16(&mut self, addr: u32) -> u8 {
        0
    }

    pub fn read32(&mut self, addr: u32) -> u8 {
        0
    }

    pub fn write8(&mut self, addr: u32, val: u8) {

    }

    pub fn write16(&mut self, addr: u32, val: u16) {

    }

    pub fn write32(&mut self, addr: u32, val: u32) {

    }
}