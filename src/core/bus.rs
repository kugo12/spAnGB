use std::io::prelude::*;
use std::fs::File;
use std::path::Path;
use std::any::type_name;

use crate::core::{PPU, Cartridge, io, io::MemoryMappedRegister};


macro_rules! get_MMIO_reg {
    ($t:ty, $self:expr, $addr:expr) => {
        match $addr {
            0 => &mut $self.ppu.dispcnt as &mut dyn MemoryMappedRegister,
            0x4 => &mut $self.ppu.dispstat,
            0x8 => &mut $self.ppu.bgcnt[0],
            0xA => &mut $self.ppu.bgcnt[1],
            0xC => &mut $self.ppu.bgcnt[2],
            0xE => &mut $self.ppu.bgcnt[3],
            0x130 => &mut $self.key_input,
            0x200 => &mut $self.ie,
            0x202 => &mut $self.ir,
            0x208 => &mut $self.ime,
            _ => &mut $self.dummy_reg,
            _ => panic!("Access to unhandled MMIO reg at: {:x} type: {:?}", $addr, type_name::<$t>())
        }
    };
}

pub struct Bus {
    wram: Box<[u8; 256*1024]>,  // on-board wram
    iwram: [u8; 32*1024],  // internal wram (on-chip)
    bios: Vec<u8>,

    ppu: PPU,
    pub cartridge: Cartridge,

    dummy_reg: io::DummyRegister,
    ime: io::InterruptMasterEnable,
    ie: io::InterruptEnable,
    ir: io::InterruptRequest,
    key_input: io::KeyInput,
}

impl Bus {
    pub fn new() -> Self {
        Self {
            wram: Box::new([0; 256*1024]),
            iwram: [0; 32*1024],
            bios: vec![],

            ppu: PPU::new(),
            cartridge: Cartridge::new(),

            dummy_reg: io::DummyRegister {},
            ime: io::InterruptMasterEnable::new(),
            ie: io::InterruptEnable::new(),
            ir: io::InterruptRequest::new(),
            key_input: io::KeyInput::new()
        }
    }

    pub fn dump_ewram(&self) {
        for i in 0..self.wram.len() {
            print!("{}", self.wram[i] as char);
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
        if self.ppu.tick() {
            self.key_input.update(&self.ppu.draw.handle);
        }
    }

    pub fn read8(&mut self, mut addr: u32) -> u8 {
        addr &= 0x0FFFFFFF;
        match addr&0x0FFFFFFF {
            0x00000000 ..= 0x00003FFF => {
                self.bios[addr as usize]
            },
            0x02000000 ..= 0x02FFFFFF => {
                addr &= 0x3FFFF;
                self.wram[addr as usize]
            },
            0x03000000 ..= 0x03FFFFFF => {
                addr &= 0x7FFF;
                self.iwram[addr as usize]
            }
            0x04000000 ..= 0x040003FE => {
                addr &= 0x3FE;
                get_MMIO_reg!(u8, self, addr).read8(addr)
            },
            0x05000000 ..= 0x07FFFFFF => {
                self.ppu.read8(addr)
            },
            0x08000000 ..= 0x0DFFFFFF => {
                addr &= 0x01FFFFFF;
                if addr as usize >= self.cartridge.rom.len() {
                    0
                } else {
                    self.cartridge.rom[addr as usize]
                }
            },
            _ => {
                println!("Unhandled read8 from {:x}", addr);
                0
            }
        }
    }

    pub fn read16(&mut self, mut addr: u32) -> u16 {
        addr &= 0x0FFFFFFE;
        match addr&0x0FFFFFFF {
            0x00000000 ..= 0x00003FFF => {
                bus_read_arr!(u16, self.bios, addr as usize)
            },
            0x02000000 ..= 0x02FFFFFF => {
                addr &= 0x3FFFF;
                bus_read_arr!(u16, self.wram, addr as usize)
            },
            0x03000000 ..= 0x03FFFFFF => {
                addr &= 0x7FFF;
                bus_read_arr!(u16, self.iwram, addr as usize)
            }
            0x04000000 ..= 0x040003FE => {
                addr &= 0x3FE;
                get_MMIO_reg!(u16, self, addr).read16(addr)
            },
            0x05000000 ..= 0x07FFFFFF => {
                self.ppu.read16(addr)
            },
            0x08000000 ..= 0x0DFFFFFF => {
                addr &= 0x01FFFFFF;
                if addr as usize >= self.cartridge.rom.len() {
                    0
                } else {
                    bus_read_arr!(u16, self.cartridge.rom, addr as usize)
                }
            },
            _ => {
                println!("Unhandled read16 from {:x}", addr);
                0
            }
        }
    }

    pub fn read32(&mut self, mut addr: u32) -> u32 {
        addr &= 0x0FFFFFFC;
        match addr&0x0FFFFFFF {
            0x00000000 ..= 0x00003FFF => {
                bus_read_arr!(u32, self.bios, addr as usize)
            },
            0x02000000 ..= 0x02FFFFFF => {
                addr &= 0x3FFFF;
                bus_read_arr!(u32, self.wram, addr as usize)
            },
            0x03000000 ..= 0x03FFFFFF => {
                addr &= 0x7FFF;
                bus_read_arr!(u32, self.iwram, addr as usize)
            }
            0x04000000 ..= 0x040003FE => {
                addr &= 0x3FE;
                get_MMIO_reg!(u32, self, addr).read32(addr)
            },
            0x05000000 ..= 0x07FFFFFF => {
                self.ppu.read32(addr)
            },
            0x08000000 ..= 0x0DFFFFFF => {
                addr &= 0x01FFFFFF;
                if addr as usize >= self.cartridge.rom.len() {
                    0
                } else {
                    bus_read_arr!(u32, self.cartridge.rom, addr as usize)
                }
            },
            _ => {
                println!("Unhandled read32 from {:x}", addr);
                0
            }
        }
    }

    pub fn write8(&mut self, mut addr: u32, val: u8) {
        addr &= 0x0FFFFFFF;
        match addr&0x0FFFFFFF {
            0x02000000 ..= 0x02FFFFFF => {
                addr &= 0x3FFFF;
                self.wram[addr as usize] = val;
            },
            0x03000000 ..= 0x03FFFFFF => {
                addr &= 0x7FFF;
                self.iwram[addr as usize] = val;
            }
            0x04000000 ..= 0x040003FE => {
                addr &= 0x3FE;
                get_MMIO_reg!(u8, self, addr).write8(addr, val);
            },
            0x05000000 ..= 0x07FFFFFF => {
                self.ppu.write16(addr, (val as u16) | ((val as u16) << 8))
            },
            _ => println!("Unhandled write8 to {:x}, value: {:x}", addr, val)
        }
    }

    pub fn write16(&mut self, mut addr: u32, val: u16) {
        addr &= 0x0FFFFFFE;
        match addr&0x0FFFFFFF {
            0x02000000 ..= 0x02FFFFFF => {
                addr &= 0x3FFFF;
                bus_write_arr!(u16, self.wram, addr as usize, val);
            },
            0x03000000 ..= 0x03FFFFFF => {
                addr &= 0x7FFF;
                bus_write_arr!(u16, self.iwram, addr as usize, val);
            }
            0x04000000 ..= 0x040003FE => {
                addr &= 0x3FE;
                get_MMIO_reg!(u16, self, addr).write16(addr, val);
            },
            0x05000000 ..= 0x07FFFFFF => {
                self.ppu.write16(addr, val)
            },
            _ => println!("Unhandled write16 to {:x}, value: {:x}", addr, val)
        }
    }

    pub fn write32(&mut self, mut addr: u32, val: u32) {
        addr &= 0x0FFFFFFC;
        match addr&0x0FFFFFFFF {
            0x00000000 ..= 0x0003FFF => {},
            0x02000000 ..= 0x02FFFFFF => {
                addr &= 0x3FFFF;
                bus_write_arr!(u32, self.wram, addr as usize, val);
            },
            0x03000000 ..= 0x03FFFFFF => {
                addr &= 0x7FFF;
                bus_write_arr!(u32, self.iwram, addr as usize, val);
            }
            0x04000000 ..= 0x040003FE => {
                addr &= 0x3FE;
                get_MMIO_reg!(u32, self, addr).write32(addr, val);
            },
            0x05000000 ..= 0x07FFFFFF => {
                self.ppu.write32(addr, val)
            },
            _ => println!("Unhandled write32 to {:x}, value: {:x}", addr, val)
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