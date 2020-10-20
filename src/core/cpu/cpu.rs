use crate::core::Bus;

extern crate itertools;
use itertools::Itertools;

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

impl From<u32> for CPU_mode {
    fn from(rhs: u32) -> Self {
        match rhs&0x1F {
            0b10000 => Self::usr,
            0b10001 => Self::fiq,
            0b10010 => Self::irq,
            0b10011 => Self::svc,
            0b10111 => Self::abt,
            0b11011 => Self::und,
            0b11111 => Self::sys,
            _ => panic!("Invalid mode: {:x}", rhs&0x1F)
        }
    }
}

#[derive(Copy, Clone, PartialEq)]
pub enum CPU_state {
    ARM,
    THUMB
}

impl From<u32> for CPU_state {
    fn from(x: u32) -> CPU_state {
        match x&1 {
            0 => CPU_state::ARM,
            1 => CPU_state::THUMB,
            _ => unreachable!()
        }
    }
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
    pub bank_reg: [[u32; 3]; 4],
    fiq_reg: [u32; 7],
    pub fiq_spsr: u32,
    reg: [u32; 7],
    pub cpsr: u32,

    pub state: CPU_state,
    pub mode: CPU_mode,
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
            mode: CPU_mode::sys,
            pipeline: [0; 3],
            lut_thumb: [CPU::undefined_opcode; 1024],
            lut_arm: [CPU::undefined_opcode; 4096]
        };

        cpu.ARM_fill_lut();
        cpu.THUMB_fill_lut();

        cpu
    }

    pub fn init(&mut self, bus: &mut Bus) {
        self.register[0] = 0x08000000;
        self.register[1] = 0xEA;
        self.register[13] = 0x3007F00;
        self.cpsr = 0x6000001F;

        self.register[15] = 0x08000000;
        self.arm_fill_pipeline(bus);
    }

    pub fn undefined_opcode<T: std::fmt::LowerHex>(&mut self, bus: &mut Bus, instr: T) {
        panic!("Undefined opcode: {:x} at PC: {:x}, pipeline: {:x}", instr, self.register[15] - (2* (self.mode as u32+1)), self.pipeline.iter().format(" "));
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

    pub fn register_write(&mut self, index: usize, value: u32, bus: &mut Bus) {
        self.register[index] = value;
        if index == 15 {
            match self.state {
                CPU_state::ARM => self.arm_refill_pipeline(bus),
                CPU_state::THUMB => self.thumb_refill_pipeline(bus)
            }
        }
    }

    #[inline]
    pub fn tick(&mut self, bus: &mut Bus) {
        if self.pipeline[2] == 0 {
            panic!("{:x}, {:x}", self.register[15] - 8, self.register[15] - 4);
        }
        if self.state == CPU_state::ARM {
            // println!("ARM Instruction {:08x} at {:x}", self.pipeline[2], self.register[15]-8);
            // self.print_regs();
            self.tick_ARM(bus);
        } else {
            // println!("THUMB Instruction {:04x} at {:x}", self.pipeline[2], self.register[15]-4);
            // self.print_regs();
            self.tick_THUMB(bus)
        }
    }

    pub fn print_regs(&self) {
        println!("r0: {:08x} r1: {:08x} r2: {:08x} r3: {:08x}", self.register[0], self.register[1], self.register[2], self.register[3]);
        println!("r4: {:08x} r5: {:08x} r6: {:08x} r7: {:08x}", self.register[4], self.register[5], self.register[6], self.register[7]);
        println!("r8: {:08x} r9: {:08x} r10: {:08x} r11: {:08x}", self.register[8], self.register[9], self.register[10], self.register[11]);
        println!("r12: {:08x} r13: {:08x} r14: {:08x} r15: {:08x}", self.register[12], self.register[13], self.register[14], self.register[15]);
        println!("cpsr: {:08x}", self.cpsr);
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
            0xF => true, //panic!("Condition 0xF is reserved"),
            _ => panic!("Undefined cond: {:x}", cond)
        }
    }

    #[inline]
    pub fn flush_pipeline(&mut self) {
        self.pipeline = [0; 3];
    }

    #[inline]
    pub fn switch_state(&mut self, bus: &mut Bus, state: u32) {
        self.state = CPU_state::from(state);
        self.set_flag(Flag::T, self.state == CPU_state::THUMB);
        match self.state {
            CPU_state::ARM => self.arm_refill_pipeline(bus),
            CPU_state::THUMB => self.thumb_refill_pipeline(bus)
        }
    }

    pub fn set_flag(&mut self, flag: Flag, val: bool) {
        self.cpsr &= !flag;
        if val {
            self.cpsr |= flag;
        }
    }
}
