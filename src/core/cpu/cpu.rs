use crate::core::Bus;

#[derive(PartialEq, Copy, Clone)]
pub enum CPU_mode {
    irq,
    svc,  // supervisor
    abt,  // abort mode
    und,  // undefined
    fiq, 
    usr,  // user
    sys,  // system
}

#[derive(Copy, Clone)]
pub enum CPU_state {
    ARM,
    THUMB
}


#[derive(Copy, Clone)]
pub enum Flag {
    N = 0x80000000,  // sign
    Z = 0x40000000,  // zero
    C = 0x20000000,  // carry (0-borrow/no carry, 1-no borrow/carry)
    V = 0x10000000,  // overflow
    I = 0x80,        // IRQ disable
    F = 0x40,        // FIQ disable
    T = 0x20,        // state bit (1 - thumb, 0 - arm)
}

impl std::ops::BitAnd<Flag> for u32 {
    type Output = Self;

    #[inline]
    fn bitand(self, rhs: Flag) -> Self {
        self & rhs as u32
    }
}

impl std::ops::BitOr<Flag> for u32 {
    type Output = Self;

    #[inline]
    fn bitor(self, rhs: Flag) -> Self {
        self | rhs as u32
    }
}

impl std::ops::Not for Flag {
    type Output = u32;

    #[inline]
    fn not(self) -> u32 {
        !(self as u32)
    }
}

impl std::ops::BitOrAssign<Flag> for u32 {
    #[inline]
    fn bitor_assign(&mut self, rhs: Flag) {
        *self = *self | rhs;
    }
}

const CPU_MODE: [u32; 7] = [0b10010, 0b10011, 0b10111, 0b11011, 0b10001, 0b10000, 0b11111];

pub struct CPU {
    pub register: [u32; 16],
    bank_reg: [[u32; 3]; 4],
    fiq_reg: [u32; 7],
    fiq_spsr: u32,
    reg: [u32; 7],
    pub cpsr: u32,

    state: CPU_state,
    mode: CPU_mode,
    pub pipeline: [u32; 3],
    pub lut_thumb: [fn(&mut CPU, &mut Bus, u16); 1024],
    pub lut_arm: [fn(&mut CPU, &mut Bus, u32); 4096],
}

impl CPU {
    pub fn new() -> Self {
        let mut cpu = Self {
            register: [0; 16],
            bank_reg: [[0; 3]; 4],
            fiq_reg: [0; 7],
            fiq_spsr: 0,
            reg: [0; 7],
            cpsr: 0,

            state: CPU_state::ARM,
            mode: CPU_mode::usr,
            pipeline: [0; 3],
            lut_thumb: [CPU::undefined_opcode; 1024],
            lut_arm: [CPU::undefined_opcode; 4096]
        };

        cpu.ARM_fill_lut();

        cpu
    }

    pub fn init(&mut self, bus: &mut Bus) {
        self.register[15] = 0x08000000;
        self.arm_fill_pipeline(bus);
    }

    pub fn undefined_opcode<T: std::fmt::LowerHex>(&mut self, bus: &mut Bus, instr: T) {
        panic!("Undefined opcode: {:x} at PC: {:x}", instr, self.register[15] - (2* (self.mode as u32+1)));
    }

    pub fn set_mode(&mut self, mode: CPU_mode) {
        use CPU_mode::*;

        if mode != self.mode {
            match self.mode {
                usr | sys => {
                    self.reg.copy_from_slice(&self.register[8..15]);
                },
                fiq => {
                    self.fiq_reg.copy_from_slice(&self.register[8..15]);
                    self.cpsr = self.fiq_spsr;
                },
                _ => {
                    self.bank_reg[self.mode as usize][0] = self.register[13];
                    self.bank_reg[self.mode as usize][1] = self.register[14];
                    self.cpsr = self.bank_reg[self.mode as usize][2];
                }
            }

            match mode {
                usr | sys => {
                    self.register[8..15].swap_with_slice(&mut self.reg);
                },
                fiq => {
                    self.register[8..15].swap_with_slice(&mut self.fiq_reg);
                    self.fiq_spsr = self.cpsr;
                },
                _ => {
                    self.register[13] = self.bank_reg[mode as usize][0];
                    self.register[14] = self.bank_reg[mode as usize][1];
                    self.bank_reg[mode as usize][2] = self.cpsr;
                }
            }

            self.cpsr = (self.cpsr & 0xFFFFFFE0) | CPU_MODE[mode as usize];
            self.mode = mode;
        }
    }

    #[inline]
    pub fn tick(&mut self, bus: &mut Bus) {
        [CPU::tick_ARM, CPU::tick_THUMB][self.state as usize](self, bus);
    }

    #[inline]
    pub fn is_condition(&self, cond: u8) -> bool {
        match cond {
            0x0 => self.cpsr&Flag::Z != 0,
            0x1 => self.cpsr&Flag::Z == 0,
            0x2 => self.cpsr&Flag::C != 0,
            0x3 => self.cpsr&Flag::C == 0,
            0x4 => self.cpsr&Flag::N != 0,
            0x5 => self.cpsr&Flag::N == 0,
            0x6 => self.cpsr&Flag::V != 0,
            0x7 => self.cpsr&Flag::V == 0,
            0x8 => (self.cpsr&Flag::C != 0) && (self.cpsr&Flag::Z == 0),
            0x9 => (self.cpsr&Flag::C == 0) || (self.cpsr&Flag::Z != 0),
            0xA => (self.cpsr&Flag::N != 0) == (self.cpsr&Flag::V != 0),
            0xB => (self.cpsr&Flag::N != 0) != (self.cpsr&Flag::V != 0),
            0xC => (self.cpsr&Flag::Z == 0) && ((self.cpsr&Flag::N != 0) == (self.cpsr&Flag::V != 0)),
            0xD => (self.cpsr&Flag::Z != 0) || ((self.cpsr&Flag::N != 0) != (self.cpsr&Flag::V != 0)),
            0xE => true,
            0xF => panic!("Condition 0xF is reserved"),
            _ => panic!("Undefined cond: {:x}", cond)
        }
    }

    #[inline]
    pub fn flush_pipeline(&mut self) {
        self.pipeline = [0; 3];
    }

    #[inline]
    pub fn switch_state(&mut self, bus: &mut Bus, state: u32) {
        self.flush_pipeline();
        match state&0x1 {
            0 => {
                self.arm_fill_pipeline(bus);
            },
            1 => {
                self.thumb_fill_pipeline(bus);
            },
            _ => unreachable!()
        }
    }

    pub fn set_flag(&mut self, flag: Flag, val: bool) {
        self.cpsr &= !flag;
        if val {
            self.cpsr |= flag;
        }
    }
}