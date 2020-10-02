use std::io::prelude::*;
use std::fs::File;
use std::path::Path;


use crate::core::{PPU, Cartridge};


pub struct Bus {
    wram: Box<[u8; 256*1024]>,  // on-board wram
    iwram: [u8; 32*1024],  // internal wram (on-chip)
    bios: Vec<u8>,

    ppu: PPU,
    pub cartridge: Cartridge,
}

impl Bus {
    pub fn new() -> Self {
        Self {
            wram: Box::new([0; 256*1024]),
            iwram: [0; 32*1024],
            bios: vec![],

            ppu: PPU::new(),
            cartridge: Cartridge::new()
        }
    }

    pub fn dump_ewram(&self) {
        for i in 0..self.wram.len() {
            print!("{:x}", self.wram[i]);
            if i%32 == 0 {
                print!("\n");
            }
        }
    }

    pub fn load_bios(&mut self, p: &Path) {
        let mut data: Vec<u8> = vec![];
        {
            let mut file = File::open(p).unwrap();
            file.read_to_end(&mut data).unwrap();
        }

        if data.len() != 16*1024 {  // if it's not 16KB
            panic!("Invalid BIOS file");
        }

        self.bios = data;
    }

    pub fn tick(&mut self) {
        self.ppu.tick();
    }

    pub fn read8(&mut self, mut addr: u32) -> u8 {
        addr &= 0x0FFFFFFF;
        match addr&0x0FFFFFFF {
            0x00000000 ..= 0x00003FFF => {
                self.bios[addr as usize]
            },
            0x02000000 ..= 0x0203FFFF => {
                addr &= 0x3FFFF;
                self.wram[addr as usize]
            },
            0x03000000 ..= 0x03007FFF => {
                addr &= 0x7FFF;
                self.iwram[addr as usize]
            }
            0x04000000 ..= 0x040003FE => {  // TODO: implement mmio
                addr &= 0x3FE;
                0
            },
            0x05000000 ..= 0x07FFFFFF => {  // TODO: map ppu memory
                0
            },
            0x08000000 ..= 0x09FFFFFF => {
                addr -= 0x08000000;
                self.cartridge.rom[addr as usize]
            }
            _ => 0
        }
    }

    pub fn read16(&mut self, mut addr: u32) -> u16 {
        addr &= 0x0FFFFFFE;
        match addr&0x0FFFFFFF {
            0x00000000 ..= 0x00003FFF => {
                let tmp = &self.bios[addr as usize..addr as usize+2];
                let tmp = [tmp[0], tmp[1]];
                u16::from_le_bytes(tmp)
            },
            0x02000000 ..= 0x0203FFFF => {
                addr &= 0x3FFFF;
                let tmp = &self.wram[addr as usize..addr as usize+2];
                let tmp = [tmp[0], tmp[1]];
                u16::from_le_bytes(tmp)
            },
            0x03000000 ..= 0x03007FFF => {
                addr &= 0x7FFF;
                let tmp = &self.iwram[addr as usize..addr as usize+2];
                let tmp = [tmp[0], tmp[1]];
                u16::from_le_bytes(tmp)
            }
            0x04000000 ..= 0x040003FE => {  // TODO: implement mmio
                addr &= 0x3FE;
                0
            },
            0x05000000 ..= 0x07FFFFFF => {  // TODO: map ppu memory
                0
            },
            0x08000000 ..= 0x09FFFFFF => {
                addr -= 0x08000000;
                let tmp = &self.cartridge.rom[addr as usize..addr as usize+2];
                let tmp = [tmp[0], tmp[1]];
                u16::from_le_bytes(tmp)
            }
            _ => 0
        }
    }

    pub fn read32(&mut self, mut addr: u32) -> u32 {
        addr &= 0x0FFFFFFC;
        match addr&0x0FFFFFFF {
            0x00000000 ..= 0x00003FFF => {
                let tmp = &self.bios[addr as usize..addr as usize+4];
                let tmp = [tmp[0], tmp[1], tmp[2], tmp[3]];
                u32::from_le_bytes(tmp)
            },
            0x02000000 ..= 0x0203FFFF => {
                addr &= 0x3FFFF;
                let tmp = &self.wram[addr as usize..addr as usize+4];
                let tmp = [tmp[0], tmp[1], tmp[2], tmp[3]];
                u32::from_le_bytes(tmp)
            },
            0x03000000 ..= 0x03007FFF => {
                addr &= 0x7FFF;
                let tmp = &self.iwram[addr as usize..addr as usize+4];
                let tmp = [tmp[0], tmp[1], tmp[2], tmp[3]];
                u32::from_le_bytes(tmp)
            }
            0x04000000 ..= 0x040003FE => {  // TODO: implement mmio
                addr &= 0x3FE;
                0
            },
            0x05000000 ..= 0x07FFFFFF => {  // TODO: map ppu memory
                0
            },
            0x08000000 ..= 0x09FFFFFF => {
                addr -= 0x08000000;
                let tmp = &self.cartridge.rom[addr as usize..addr as usize+4];
                let tmp = [tmp[0], tmp[1], tmp[2], tmp[3]];
                u32::from_le_bytes(tmp)
            }
            _ => 0
        }
    }

    pub fn write8(&mut self, mut addr: u32, val: u8) {
        addr &= 0x0FFFFFFF;
        match addr&0x0FFFFFFF {
            0x02000000 ..= 0x0203FFFF => {
                addr &= 0x3FFFF;
                self.wram[addr as usize] = val;
            },
            0x03000000 ..= 0x03007FFF => {
                addr &= 0x7FFF;
                self.iwram[addr as usize] = val;
            }
            0x04000000 ..= 0x040003FE => {  // TODO: implement mmio
                addr &= 0x3FE;
                
            },
            0x05000000 ..= 0x07FFFFFF => {  // TODO: map ppu memory
                
            },
            _ => ()
        }
    }

    pub fn write16(&mut self, mut addr: u32, val: u16) {
        let val = val.to_le_bytes();
        addr &= 0x0FFFFFFE;
        match addr&0x0FFFFFFF {
            0x02000000 ..= 0x0203FFFF => {
                addr &= 0x3FFFF;
                self.wram[addr as usize] = val[0];
                self.wram[addr as usize+1] = val[1];
            },
            0x03000000 ..= 0x03007FFF => {
                addr &= 0x7FFF;
                self.iwram[addr as usize] = val[0];
                self.iwram[addr as usize+1] = val[1];
            }
            0x04000000 ..= 0x040003FE => {  // TODO: implement mmio
                addr &= 0x3FE;

            },
            0x05000000 ..= 0x07FFFFFF => {  // TODO: map ppu memory
                
            },
            _ => ()
        }
    }

    pub fn write32(&mut self, mut addr: u32, val: u32) {
        let val = val.to_le_bytes();
        addr &= 0x0FFFFFFC;
        match addr&0x0FFFFFFF {
            0x02000000 ..= 0x0203FFFF => {
                addr &= 0x3FFFF;
                self.wram[addr as usize] = val[0];
                self.wram[addr as usize+1] = val[1];
                self.wram[addr as usize+2] = val[2];
                self.wram[addr as usize+3] = val[3]; 
            },
            0x03000000 ..= 0x03007FFF => {
                addr &= 0x7FFF;
                self.iwram[addr as usize] = val[0];
                self.iwram[addr as usize+1] = val[1];
                self.iwram[addr as usize+2] = val[2];
                self.iwram[addr as usize+3] = val[3];
            }
            0x04000000 ..= 0x040003FE => {  // TODO: implement mmio
                addr &= 0x3FE;

            },
            0x05000000 ..= 0x07FFFFFF => {  // TODO: map ppu memory
                
            },
            _ => ()
        }

    }
}


#[cfg(test)]
mod test {
    use crate::core::Bus;
    use rand::Rng;
    #[test]
    fn rw_equal_ram_test() {
        const ADDRESS: [[u32; 2]; 2] = [[0x02000000, 0x0203FFFF], [0x03000000, 0x03007FFF]];
        let mut rng = rand::thread_rng();

        for i in ADDRESS.iter() {
            let mut bus = Bus::new();
            for j in i[0] ..= i[1] {
                let rval: u8 = rng.gen();
                bus.write8(j, rval);
                assert_eq!(bus.read8(j), rval);
            }
        }

        for i in ADDRESS.iter() {
            let mut bus = Bus::new();
            for j in i[0] ..= i[1] {
                let rval: u16 = rng.gen();
                bus.write16(j, rval);
                assert_eq!(bus.read16(j), rval);
            }
        }

        for i in ADDRESS.iter() {
            let mut bus = Bus::new();
            for j in i[0] ..= i[1] {
                let rval: u32 = rng.gen();
                bus.write32(j, rval);
                assert_eq!(bus.read32(j), rval);
            }
        }
    }
}