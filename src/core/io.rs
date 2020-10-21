use raylib::RaylibHandle;
use raylib::consts::KeyboardKey;


pub trait MemoryMappedRegister {
    fn read8(&mut self, addr: u32) -> u8;
    fn read16(&mut self, addr: u32) -> u16;
    fn read32(&mut self, addr: u32) -> u32;

    fn write8(&mut self, addr: u32, val: u8);
    fn write16(&mut self, addr: u32, val: u16);
    fn write32(&mut self, addr: u32, val: u32);
}


pub struct DummyRegister {}
impl MemoryMappedRegister for DummyRegister {
    fn read8(&mut self, addr: u32) -> u8 { 0 }
    fn read16(&mut self, addr: u32) -> u16 { 0 }
    fn read32(&mut self, addr: u32) -> u32 { 0 }

    fn write8(&mut self, addr: u32, val: u8) {}
    fn write16(&mut self, addr: u32, val: u16) {}
    fn write32(&mut self, addr: u32, val: u32) {}
}


pub struct InterruptMasterEnable {
    enabled: bool
}

impl InterruptMasterEnable {
    pub fn new() -> Self {
        Self {
            enabled: false
        }
    }
}

impl MemoryMappedRegister for InterruptMasterEnable {
    fn read8(&mut self, addr: u32) -> u8 {
        self.enabled as u8
    }

    fn read16(&mut self, addr: u32) -> u16 {
        self.enabled as u16
    }

    fn read32(&mut self, addr: u32) -> u32 {
        self.enabled as u32
    }

    fn write8(&mut self, addr: u32, val: u8) {
        self.enabled = val&0x1 == 0x1;
    }

    fn write16(&mut self, addr: u32, val: u16) {
        self.enabled = val&0x1 == 0x1;
    }

    fn write32(&mut self, addr: u32, val: u32) {
        self.enabled = val&0x1 == 0x1;
    }
}


const KEY_MAPPING: [KeyboardKey; 10] = [
    KeyboardKey::KEY_Z,  // A
    KeyboardKey::KEY_X,  // B
    KeyboardKey::KEY_S,  // Select
    KeyboardKey::KEY_A,  // Start
    KeyboardKey::KEY_RIGHT,  // Right
    KeyboardKey::KEY_LEFT,  // Left
    KeyboardKey::KEY_UP,  // Up
    KeyboardKey::KEY_DOWN,  // Down
    KeyboardKey::KEY_W,  // R
    KeyboardKey::KEY_Q   // L
];

pub struct KeyInput {
    value: u16
}

impl KeyInput {
    pub fn new() -> Self {
        Self {
            value: 0xFFFF
        }
    }

    pub fn update(&mut self, handle: &RaylibHandle) {
        self.value = 0;
        for (index, key) in KEY_MAPPING.iter().enumerate() {
            if handle.is_key_up(*key) {
                self.value |= 1 << index;
            }
        }
    }
}

impl MemoryMappedRegister for KeyInput {
    fn read8(&mut self, addr: u32) -> u8 {
        if addr&0x1 != 0 {
            (self.value >> 8) as u8
        } else {
            self.value as u8
        }
    }

    fn read16(&mut self, addr: u32) -> u16 { self.value }
    fn read32(&mut self, addr: u32) -> u32 { self.value as u32 }

    fn write8(&mut self, addr: u32, val: u8) {}
    fn write16(&mut self, addr: u32, val: u16) {}
    fn write32(&mut self, addr: u32, val: u32) {}
}