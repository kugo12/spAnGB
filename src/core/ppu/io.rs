use crate::core::io::MemoryMappedRegister;
use crate::core::PPU;

// 0-2   BG Mode                (0-5=Video Mode 0-5, 6-7=Prohibited)
// 3     Reserved / CGB Mode    (0=GBA, 1=CGB; can be set only by BIOS opcodes)
// 4     Display Frame Select   (0-1=Frame 0-1) (for BG Modes 4,5 only)
// 5     H-Blank Interval Free  (1=Allow access to OAM during H-Blank)
// 6     OBJ Character VRAM Mapping (0=Two dimensional, 1=One dimensional)
// 7     Forced Blank           (1=Allow FAST access to VRAM,Palette,OAM)
// 8     Screen Display BG0  (0=Off, 1=On)
// 9     Screen Display BG1  (0=Off, 1=On)
// 10    Screen Display BG2  (0=Off, 1=On)
// 11    Screen Display BG3  (0=Off, 1=On)
// 12    Screen Display OBJ  (0=Off, 1=On)
// 13    Window 0 Display Flag   (0=Off, 1=On)
// 14    Window 1 Display Flag   (0=Off, 1=On)
// 15    OBJ Window Display Flag (0=Off, 1=On)
#[repr(u16)]
pub enum DisplayControlFlag {
    DISPLAY_FRAME_SELECT = 0x10,
    HBLANK_INTERVAL_FREE = 0x20,
    OBJ_VRAM_MAPPING = 0x40,
    FORCED_BLANK = 0x80,
    DISPLAY_BG0 = 0x100,
    DISPLAY_BG1 = 0x200,
    DISPLAY_BG2 = 0x400, 
    DISPLAY_BG3 = 0x800, 
    DISPLAY_OBJ = 0x1000, 
    DISPLAY_WIN0 = 0x2000,
    DISPLAY_WIN1 = 0x4000,
    DISPLAY_WIN_OBJ = 0x8000
}


pub struct DisplayControl {
    pub bg_mode: u16,
    value: u16,
}

impl DisplayControl {
    pub fn new() -> Self {
        Self {
            bg_mode: 0,
            value: 0
        }
    }

    pub fn is(&self, flag: DisplayControlFlag) -> bool {
        self.value&flag as u16 != 0
    }
}

impl MemoryMappedRegister for DisplayControl {
    fn read8(&mut self, addr: u32) -> u8 {
        if addr&0x1 != 0 {
            (self.value >> 8) as u8
        } else {
            self.value as u8
        }
    }
    fn read16(&mut self, addr: u32) -> u16 { self.value }

    fn write8(&mut self, addr: u32, val: u8) {
        if addr&0x1 != 0 {
            self.write16(0, ((val as u16) << 8) | (self.value&0xFF));
        } else {
            self.write16(0, val as u16 | (self.value&0xFF00))
        }
    }

    fn write16(&mut self, addr: u32, val: u16) {
        self.value = val;
        self.bg_mode = val&0x3;
    }

    fn write32(&mut self, addr: u32, val: u32) {}
    fn read32(&mut self, addr: u32) -> u32 { 0 }
}


// 0     V-Blank flag   (Read only) (1=VBlank) (set in line 160..226; not 227) (R)
// 1     H-Blank flag   (Read only) (1=HBlank) (toggled in all lines, 0..227) (R)
// 2     V-Counter flag (Read only) (1=Match)  (set in selected line)     (R)
// 3     V-Blank IRQ Enable         (1=Enable)                          (R/W)
// 4     H-Blank IRQ Enable         (1=Enable)                          (R/W)
// 5     V-Counter IRQ Enable       (1=Enable)                          (R/W)
// 8-15  V-Count Setting (LYC)      (0..227)                            (R/W)


#[repr(u16)]
pub enum DisplayStatFlag {
    VBLANK = 0x1,
    HBLANK = 0x2,
    VCOUNTER = 0x4,
    VBLANK_IRQ = 0x8,
    HBLANK_IRQ = 0x10,
    VCOUNTER_IRQ = 0x20
}

pub struct DisplayStat {
    value: u16,
    lyc: u16,
    fr: u8
}

impl DisplayStat {
    pub fn new() -> Self {
        Self {
            value: 0xFFFF,
            lyc: 0,
            fr: 100
        }
    }

    pub fn set(&mut self, flag: DisplayStatFlag) {
        self.value |= flag as u16;
    }

    pub fn unset(&mut self, flag: DisplayStatFlag) {
        self.value &= !(flag as u16);
    }

    pub fn is(&self, flag: DisplayStatFlag) -> bool {
        self.value&flag as u16 != 0
    }
}


impl MemoryMappedRegister for DisplayStat {
    fn read8(&mut self, addr: u32) -> u8 {
        println!("stat r8");
        if addr&0x1 != 0 {
            self.lyc as u8
        } else {
            self.value as u8
        }
    }

    fn write8(&mut self, addr: u32, val: u8) {
        if addr&0x1 != 0 {
            self.lyc = val as u16;
        } else {
            self.value = val as u16&0xFFF8;
        }
    }

    fn read16(&mut self, addr: u32) -> u16 {
        (self.lyc << 8) | self.value
    }

    fn write16(&mut self, addr: u32, val: u16) {
        self.lyc = val >> 8;
        self.value = val&0xF8;
    }

    fn read32(&mut self, addr: u32) -> u32 {
        self.read16(addr) as u32
    }

    fn write32(&mut self, addr: u32, val: u32) {}
}


// 0-7   Current Scanline (LY)      (0..227)                              (R)
// 8     Not used (0) / NDS: MSB of Current Scanline (LY.Bit8) (0..262)   (R)
// 9-15  Not Used (0)

pub struct VCount {
    pub ly: u16
}

impl VCount {
    pub fn new() -> Self {
        Self {
            ly: 0
        }
    }
}

impl MemoryMappedRegister for VCount {
    fn read8(&mut self, addr: u32) -> u8 {
        if addr&0x1 != 0 {
            0
        } else {
            self.ly as u8
        }
    }

    fn read16(&mut self, addr: u32) -> u16 {
        self.ly
    }

    fn write8(&mut self, addr: u32, val: u8) {}
    fn write16(&mut self, addr: u32, val: u16) {}
    fn read32(&mut self, addr: u32) -> u32 { self.ly as u32 }
    fn write32(&mut self, addr: u32, val: u32) {}
}
