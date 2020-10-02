use crate::core::{CPU, Bus, Flag};
use crate::core::utils::is_bit_set;
use crate::core::cpu::CPU_mode;


impl CPU {
    #[inline]
    fn ARM_get_2nd_operand(&mut self, instr: u32) -> (u32, bool) {
        if is_bit_set(instr, 25) {  // immediate
            ((instr&0xFF).rotate_right((instr&0xF00) >> 8), false)
        } else {  // register
            self.barrel_shifter_operand(instr>>4, self.register[(instr&0xF) as usize])
        }
    }

    #[inline]
    pub fn ARM_AND(&mut self, bus: &mut Bus, instr: u32) {
        let op1 = self.register[((instr >> 16)&0xF) as usize];
        let (op2, carry) = self.ARM_get_2nd_operand(instr);
        let tmp = op1&op2;
        self.register_write(((instr >> 12)&0xF) as usize, tmp, bus);

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::C, carry);
            self.set_flag(Flag::Z, tmp == 0);
            self.set_flag(Flag::N, tmp&0x80000000 != 0);
        }
    }

    #[inline]
    pub fn ARM_EOR(&mut self, bus: &mut Bus, instr: u32) {  // it's just XOR lol
        let op1 = self.register[((instr >> 16)&0xF) as usize];
        let (op2, carry) = self.ARM_get_2nd_operand(instr);
        let tmp= op1^op2;
        self.register_write(((instr >> 12)&0xF) as usize, tmp, bus);

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::C, carry);
            self.set_flag(Flag::Z, tmp == 0);
            self.set_flag(Flag::N, tmp&0x80000000 != 0);
        }
    }

    #[inline]
    pub fn ARM_SUB(&mut self, bus: &mut Bus, instr: u32) {
        let op1 = self.register[((instr >> 16)&0xF) as usize];
        let (op2, carry) = self.ARM_get_2nd_operand(instr);
        let tmp = op1-op2;
        self.register_write(((instr >> 12)&0xF) as usize, tmp, bus);

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::C, carry);
            self.set_flag(Flag::Z, tmp == 0);
            self.set_flag(Flag::N, tmp&0x80000000 != 0);
        }
    }

    #[inline]
    pub fn ARM_RSB(&mut self, bus: &mut Bus, instr: u32) {
        let op1 = self.register[((instr >> 16)&0xF) as usize];
        let (op2, carry) = self.ARM_get_2nd_operand(instr);
        let tmp = op2-op1;
        self.register_write(((instr >> 12)&0xF) as usize, tmp, bus);

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::C, carry);
            self.set_flag(Flag::Z, tmp == 0);
            self.set_flag(Flag::N, tmp&0x80000000 != 0);
        }
    }

    #[inline]
    pub fn ARM_ADD(&mut self, bus: &mut Bus, instr: u32) {
        let op1 = self.register[((instr >> 16)&0xF) as usize];
        let (op2, carry) = self.ARM_get_2nd_operand(instr);
        let tmp = op1+op2;
        self.register_write(((instr >> 12)&0xF) as usize, tmp, bus);

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::C, carry);
            self.set_flag(Flag::Z, tmp == 0);
            self.set_flag(Flag::N, tmp&0x80000000 != 0);
        }
    }

    #[inline]
    pub fn ARM_ADC(&mut self, bus: &mut Bus, instr: u32) {
        let op1 = self.register[((instr >> 16)&0xF) as usize];
        let (op2, carry) = self.ARM_get_2nd_operand(instr);
        let c = (self.cpsr&Flag::C != 0) as u32;
        let tmp = op1+op2+c;
        self.register_write(((instr >> 12)&0xF) as usize, tmp, bus);

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::C, carry);
            self.set_flag(Flag::Z, tmp == 0);
            self.set_flag(Flag::N, tmp&0x80000000 != 0);
        }
    }

    #[inline]
    pub fn ARM_SBC(&mut self, bus: &mut Bus, instr: u32) {
        let op1 = self.register[((instr >> 16)&0xF) as usize];
        let (op2, carry) = self.ARM_get_2nd_operand(instr);
        let c = (self.cpsr&Flag::C != 0) as u32;
        let tmp = op1-op2+c-1; 
        self.register_write(((instr >> 12)&0xF) as usize, tmp, bus);

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::C, carry);
            self.set_flag(Flag::Z, tmp == 0);
            self.set_flag(Flag::N, tmp&0x80000000 != 0);
        }
    }

    #[inline]
    pub fn ARM_RSC(&mut self, bus: &mut Bus, instr: u32) {
        let op1 = self.register[((instr >> 16)&0xF) as usize];
        let (op2, carry) = self.ARM_get_2nd_operand(instr);
        let c = (self.cpsr&Flag::C != 0) as u32;
        let tmp = op2-op1+c-1;
        self.register_write(((instr >> 12)&0xF) as usize, tmp, bus);

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::C, carry);
            self.set_flag(Flag::Z, tmp == 0);
            self.set_flag(Flag::N, tmp&0x80000000 != 0);
        }
    }

    #[inline]
    pub fn ARM_TST(&mut self, bus: &mut Bus, instr: u32) {
        let op1 = self.register[((instr >> 16)&0xF) as usize];
        let (op2, carry) = self.ARM_get_2nd_operand(instr);
        let tmp = op1&op2;

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::C, carry);
            self.set_flag(Flag::Z, tmp == 0);
            self.set_flag(Flag::N, tmp&0x80000000 != 0);
        }
    }

    #[inline]
    pub fn ARM_TEQ(&mut self, bus: &mut Bus, instr: u32) {
        let op1 = self.register[((instr >> 16)&0xF) as usize];
        let (op2, carry) = self.ARM_get_2nd_operand(instr);
        let tmp = op1^op2;

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::C, carry);
            self.set_flag(Flag::Z, tmp == 0);
            self.set_flag(Flag::N, tmp&0x80000000 != 0);
        }
    }

    #[inline]
    pub fn ARM_CMP(&mut self, bus: &mut Bus, instr: u32) {
        let op1 = self.register[((instr >> 16)&0xF) as usize];
        let (op2, carry) = self.ARM_get_2nd_operand(instr);
        let tmp = op1-op2;

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::C, carry);
            self.set_flag(Flag::Z, tmp == 0);
            self.set_flag(Flag::N, tmp&0x80000000 != 0);
        }
    }

    #[inline]
    pub fn ARM_CMN(&mut self, bus: &mut Bus, instr: u32) {
        let op1 = self.register[((instr >> 16)&0xF) as usize];
        let (op2, carry) = self.ARM_get_2nd_operand(instr);
        let tmp = op1+op2;

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::C, carry);
            self.set_flag(Flag::Z, tmp == 0);
            self.set_flag(Flag::N, tmp&0x80000000 != 0);
        }
    }

    #[inline]
    pub fn ARM_ORR(&mut self, bus: &mut Bus, instr: u32) {
        let op1 = self.register[((instr >> 16)&0xF) as usize];
        let (op2, carry) = self.ARM_get_2nd_operand(instr);
        let tmp= op1|op2;
        self.register_write(((instr >> 12)&0xF) as usize, tmp, bus);

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::C, carry);
            self.set_flag(Flag::Z, tmp == 0);
            self.set_flag(Flag::N, tmp&0x80000000 != 0);
        }
    }

    #[inline]
    pub fn ARM_MOV(&mut self, bus: &mut Bus, instr: u32) {
        let (op2, carry) = self.ARM_get_2nd_operand(instr);
        self.register_write(((instr >> 12)&0xF) as usize, op2, bus);

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::C, carry);
            self.set_flag(Flag::Z, op2 == 0);
            self.set_flag(Flag::N, op2&0x80000000 != 0);
        }
    }

    #[inline]
    pub fn ARM_BIC(&mut self, bus: &mut Bus, instr: u32) {
        let op1 = self.register[((instr >> 16)&0xF) as usize];
        let (op2, carry) = self.ARM_get_2nd_operand(instr);
        let tmp = op1& !op2;
        self.register_write(((instr >> 12)&0xF) as usize, tmp, bus);

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::C, carry);
            self.set_flag(Flag::Z, tmp == 0);
            self.set_flag(Flag::N, tmp&0x80000000 != 0);
        }
    }

    #[inline]
    pub fn ARM_MVN(&mut self, bus: &mut Bus, instr: u32) {
        let (op2, carry) = self.ARM_get_2nd_operand(instr);
        self.register_write(((instr >> 12)&0xF) as usize, !op2, bus);

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::C, carry);
            self.set_flag(Flag::Z, !op2 == 0);
            self.set_flag(Flag::N, !op2 &0x80000000 != 0);
        }
    }

    #[inline]
    pub fn ARM_MRS(&mut self, bus: &mut Bus, instr: u32) {
        let dest = ((instr >> 12)&0xF) as usize;
        let psr = if is_bit_set(instr, 22) {  // current mode spsr
            match self.mode {
                CPU_mode::sys | CPU_mode::usr => self.register[dest],
                CPU_mode::fiq => self.fiq_spsr,
                _ => self.bank_reg[self.mode as usize][2]
            }
        } else {
            self.cpsr
        };
        
        self.register_write(dest, psr, bus);
    }

    #[inline]
    pub fn ARM_MSR(&mut self, bus: &mut Bus, instr: u32) {
        let src = if is_bit_set(instr, 25) {
            (instr&0xFF).rotate_right((instr&0xF00) >> 8)
        } else {
            self.register[(instr&0xF) as usize]
        };

        let dest = if is_bit_set(instr, 22) {  // current mode spsr
            match self.mode {
                CPU_mode::sys | CPU_mode::usr => return,
                CPU_mode::fiq => &mut self.fiq_spsr,
                _ => &mut self.bank_reg[self.mode as usize][2]
            }
        } else {
            &mut self.cpsr
        };

        let mask = if self.mode == CPU_mode::usr {
            0xF0000000  // in user mode only top 4 bits can change
        } else {
            0xFFFFFFFF
        };

        *dest = (*dest& !mask) | (src&mask);
    }
}