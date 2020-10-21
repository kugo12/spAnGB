use crate::core::{CPU, Bus, Flag};
use crate::core::utils::is_bit_set;
use crate::core::cpu::CPU_mode;


impl CPU {
    #[inline]
    fn ARM_get_2nd_operand(&mut self, instr: u32) -> (u32, bool) {
        if is_bit_set(instr, 25) {  // immediate
            self.barrel_shifter_rotate_right(instr&0xFF, (instr&0xF00) >> 7)
        } else {  // register
            self.barrel_shifter_operand(instr>>4, self.register[(instr&0xF) as usize])
        }
    }

    #[inline(always)]
    pub fn ARM_get_operands(&mut self, instr: u32) -> (u32, (u32, bool)) {
        if !is_bit_set(instr, 25) && is_bit_set(instr, 4) {
            self.register[15] += 4;
            let ops = (
                self.register[((instr >> 16)&0xF) as usize],
                self.ARM_get_2nd_operand(instr)
            );
            self.register[15] -= 4;
            ops
        } else {
            (
                self.register[((instr >> 16)&0xF) as usize],
                self.ARM_get_2nd_operand(instr)
            )
        }
    }

    #[inline]
    pub fn ARM_AND(&mut self, bus: &mut Bus, instr: u32) {
        let (op1, (op2, carry)) = self.ARM_get_operands(instr);
        let tmp = op1&op2;
        self.register_write(((instr >> 12)&0xF) as usize, tmp, bus);

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::C, carry);
            self.set_flag(Flag::Z, tmp == 0);
            self.set_flag(Flag::N, tmp&0x80000000 != 0);
        }
    }

    #[inline]
    pub fn ARM_EOR(&mut self, bus: &mut Bus, instr: u32) {
        let (op1, (op2, carry)) = self.ARM_get_operands(instr);
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
        let (op1, (op2, _)) = self.ARM_get_operands(instr);
        let (tmp, overflow) = (op1 as i32).overflowing_sub(op2 as i32);
        let d = ((instr >> 12)&0xF) as usize;
        self.register_write(d, tmp as u32, bus);

        if is_bit_set(instr, 20) {
            if d == 15 {
                match self.mode {
                    CPU_mode::sys | CPU_mode::usr => {},
                    CPU_mode::fiq => self.set_mode(CPU_mode::from(self.fiq_spsr)),
                    _ => self.set_mode(CPU_mode::from(self.bank_reg[self.mode as usize][2]))
                }
            }
            self.set_flag(Flag::C, tmp as u32 <= op1);
            self.set_flag(Flag::Z, tmp == 0);
            self.set_flag(Flag::N, tmp < 0);
            self.set_flag(Flag::V, overflow);
        }
    }

    #[inline]
    pub fn ARM_RSB(&mut self, bus: &mut Bus, instr: u32) {
        let (op1, (op2, _)) = self.ARM_get_operands(instr);
        let (tmp, overflow) = (op2 as i32).overflowing_sub(op1 as i32);
        self.register_write(((instr >> 12)&0xF) as usize, tmp as u32, bus);

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::C, tmp as u32 <= op2);
            self.set_flag(Flag::Z, tmp == 0);
            self.set_flag(Flag::N, tmp < 0);
            self.set_flag(Flag::V, overflow)
        }
    }

    #[inline]
    pub fn ARM_ADD(&mut self, bus: &mut Bus, instr: u32) {
        let (op1, (op2, _)) = self.ARM_get_operands(instr);
        let (tmp, overflow) = (op1 as i32).overflowing_add(op2 as i32);
        self.register_write(((instr >> 12)&0xF) as usize, tmp as u32, bus);

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::C, op1.overflowing_add(op2).1);
            self.set_flag(Flag::Z, tmp == 0);
            self.set_flag(Flag::N, tmp < 0);
            self.set_flag(Flag::V, overflow);
        }
    }

    #[inline]
    pub fn ARM_ADC(&mut self, bus: &mut Bus, instr: u32) {
        let (op1, (op2, _)) = self.ARM_get_operands(instr);
        let c = (self.cpsr&Flag::C != 0) as u64;
        let tmp = op1 as u64+op2 as u64+c;
        self.register_write(((instr >> 12)&0xF) as usize, tmp as u32, bus);

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::C, tmp > u32::MAX as u64);
            self.set_flag(Flag::Z, tmp as u32 == 0);
            self.set_flag(Flag::N, tmp&0x80000000 != 0);
            self.set_flag(Flag::V, (op1 as i32).overflowing_add((op2+c as u32) as i32).1);
        }
    }

    #[inline]
    pub fn ARM_SBC(&mut self, bus: &mut Bus, instr: u32) {
        let (op1, (op2, _)) = self.ARM_get_operands(instr);
        let c = (self.cpsr&Flag::C != 0) as u32;
        let (tmp, overflow) = (op1 as i32).overflowing_sub((op2+1-c) as i32);
        self.register_write(((instr >> 12)&0xF) as usize, tmp as u32, bus);

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::C, op2 as u64 - c as u64 + 1 <= op1 as u64);
            self.set_flag(Flag::Z, tmp == 0);
            self.set_flag(Flag::N, tmp < 0);
            self.set_flag(Flag::V, overflow);
        }
    }

    #[inline]
    pub fn ARM_RSC(&mut self, bus: &mut Bus, instr: u32) {
        let (op1, (op2, _)) = self.ARM_get_operands(instr);
        let c = (self.cpsr&Flag::C != 0) as u32;
        let (tmp, overflow) = (op2 as i32).overflowing_sub((op1+1-c) as i32);
        self.register_write(((instr >> 12)&0xF) as usize, tmp as u32, bus);

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::C, op1 as u64 - c as u64 + 1 <= op2 as u64);
            self.set_flag(Flag::Z, tmp == 0);
            self.set_flag(Flag::N, tmp < 0);
            self.set_flag(Flag::V, overflow);
        }
    }

    #[inline]
    pub fn ARM_TST(&mut self, bus: &mut Bus, instr: u32) {
        let (op1, (op2, carry)) = self.ARM_get_operands(instr);
        let tmp = op1&op2;

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::C, carry);
            self.set_flag(Flag::Z, tmp == 0);
            self.set_flag(Flag::N, tmp&0x80000000 != 0);
        }
    }

    #[inline]
    pub fn ARM_TEQ(&mut self, bus: &mut Bus, instr: u32) {
        let (op1, (op2, carry)) = self.ARM_get_operands(instr);
        let tmp = op1^op2;

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::C, carry);
            self.set_flag(Flag::Z, tmp == 0);
            self.set_flag(Flag::N, tmp&0x80000000 != 0);
        }
    }

    #[inline]
    pub fn ARM_CMP(&mut self, bus: &mut Bus, instr: u32) {
        let (op1, (op2, _)) = self.ARM_get_operands(instr);
        let (tmp, overflow) = (op1 as i32).overflowing_sub(op2 as i32);

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::C, tmp as u32 <= op1);
            self.set_flag(Flag::Z, tmp == 0);
            self.set_flag(Flag::N, tmp < 0);
            self.set_flag(Flag::V, overflow);
        }
    }

    #[inline]
    pub fn ARM_CMN(&mut self, bus: &mut Bus, instr: u32) {
        let (op1, (op2, _)) = self.ARM_get_operands(instr);
        let (tmp, overflow) = (op1 as i32).overflowing_add(op2 as i32);

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::C, op1.overflowing_add(op2).1);
            self.set_flag(Flag::Z, tmp == 0);
            self.set_flag(Flag::N, tmp < 0);
            self.set_flag(Flag::V, overflow);
        }
    }

    #[inline]
    pub fn ARM_ORR(&mut self, bus: &mut Bus, instr: u32) {
        let (op1, (op2, carry)) = self.ARM_get_operands(instr);
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
        let (_, (op2, carry)) = self.ARM_get_operands(instr);
        self.register_write(((instr >> 12)&0xF) as usize, op2, bus);

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::C, carry);
            self.set_flag(Flag::Z, op2 == 0);
            self.set_flag(Flag::N, op2&0x80000000 != 0);
        }
    }

    #[inline]
    pub fn ARM_BIC(&mut self, bus: &mut Bus, instr: u32) {
        let (op1, (op2, carry)) = self.ARM_get_operands(instr);
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
        let (_, (op2, carry)) = self.ARM_get_operands(instr);
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
                CPU_mode::sys | CPU_mode::usr => self.cpsr, // ???????????? todo: research
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
            (instr&0xFF).rotate_right((instr&0xF00) >> 7)
        } else {
            self.register[(instr&0xF) as usize]
        };


        let mask = if self.mode == CPU_mode::usr {
            0xF0000000  // in user mode only top 4 bits can change
        } else {
            (is_bit_set(instr, 16) as u32*0xFF)
            | (is_bit_set(instr, 17) as u32*0xFF00)
            | (is_bit_set(instr, 18) as u32*0xFF0000)
            | (is_bit_set(instr, 19) as u32*0xFF000000)
        };

        if is_bit_set(instr, 22) {  // current mode spsr
            let dest = match self.mode {
                CPU_mode::sys | CPU_mode::usr => return,
                CPU_mode::fiq => &mut self.fiq_spsr,
                _ => &mut self.bank_reg[self.mode as usize][2]
            };
            *dest = (*dest& !mask) | (src&mask);
        } else {
            self.cpsr = (self.cpsr& !mask) | (src&mask);
            self.set_mode(CPU_mode::from(self.cpsr));
        }
    }
}