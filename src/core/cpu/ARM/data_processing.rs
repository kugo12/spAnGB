use crate::core::{CPU, Bus, Flag};
use crate::core::utils::is_bit_set;

impl CPU {
    #[inline]
    fn ARM_get_2nd_operand(&mut self, instr: u32) -> (u32, bool) {
        if is_bit_set(instr, 25) {  // immediate
            ((instr&0xFF).rotate_right((instr&0xF00) >> 7), false)
        } else {  // register
            self.barrel_shifter_operand(instr>>4, self.register[(instr&0xF) as usize])
        }
    }

    #[inline]
    pub fn ARM_AND(&mut self, bus: &mut Bus, instr: u32) {
        let op1 = self.register[((instr >> 16)&0xF) as usize];
        let (op2, carry) = self.ARM_get_2nd_operand(instr);
        let dest = &mut self.register[((instr >> 12)&0xF) as usize];
        *dest = op1&op2;
        let dest = *dest;

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::C, carry);
            self.set_flag(Flag::Z, dest == 0);
            self.set_flag(Flag::N, dest&0x80000000 != 0);
        }
    }

    #[inline]
    pub fn ARM_EOR(&mut self, bus: &mut Bus, instr: u32) {  // it's just XOR lol
        let op1 = self.register[((instr >> 16)&0xF) as usize];
        let (op2, carry) = self.ARM_get_2nd_operand(instr);
        let dest = &mut self.register[((instr >> 12)&0xF) as usize];
        *dest = op1^op2;
        let dest = *dest;

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::C, carry);
            self.set_flag(Flag::Z, dest == 0);
            self.set_flag(Flag::N, dest&0x80000000 != 0);
        }
    }

    #[inline]
    pub fn ARM_SUB(&mut self, bus: &mut Bus, instr: u32) {
        let op1 = self.register[((instr >> 16)&0xF) as usize];
        let (op2, carry) = self.ARM_get_2nd_operand(instr);
        let dest = &mut self.register[((instr >> 12)&0xF) as usize];
        *dest = op1-op2;
        let dest = *dest;

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::C, carry);
            self.set_flag(Flag::Z, dest == 0);
            self.set_flag(Flag::N, dest&0x80000000 != 0);
        }
    }

    #[inline]
    pub fn ARM_RSB(&mut self, bus: &mut Bus, instr: u32) {
        let op1 = self.register[((instr >> 16)&0xF) as usize];
        let (op2, carry) = self.ARM_get_2nd_operand(instr);
        let dest = &mut self.register[((instr >> 12)&0xF) as usize];
        *dest = op2-op1;
        let dest = *dest;

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::C, carry);
            self.set_flag(Flag::Z, dest == 0);
            self.set_flag(Flag::N, dest&0x80000000 != 0);
        }
    }

    #[inline]
    pub fn ARM_ADD(&mut self, bus: &mut Bus, instr: u32) {
        let op1 = self.register[((instr >> 16)&0xF) as usize];
        let (op2, carry) = self.ARM_get_2nd_operand(instr);
        let dest = &mut self.register[((instr >> 12)&0xF) as usize];
        *dest = op1+op2;
        let dest = *dest;

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::C, carry);
            self.set_flag(Flag::Z, dest == 0);
            self.set_flag(Flag::N, dest&0x80000000 != 0);
        }
    }

    #[inline]
    pub fn ARM_ADC(&mut self, bus: &mut Bus, instr: u32) {
        let op1 = self.register[((instr >> 16)&0xF) as usize];
        let (op2, carry) = self.ARM_get_2nd_operand(instr);
        let dest = &mut self.register[((instr >> 12)&0xF) as usize];
        let c = (self.cpsr&Flag::C != 0) as u32;
        *dest = op1+op2+c;
        let dest = *dest;

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::C, carry);
            self.set_flag(Flag::Z, dest == 0);
            self.set_flag(Flag::N, dest&0x80000000 != 0);
        }
    }

    #[inline]
    pub fn ARM_SBC(&mut self, bus: &mut Bus, instr: u32) {
        let op1 = self.register[((instr >> 16)&0xF) as usize];
        let (op2, carry) = self.ARM_get_2nd_operand(instr);
        let dest = &mut self.register[((instr >> 12)&0xF) as usize];
        let c = (self.cpsr&Flag::C != 0) as u32;
        *dest = op1-op2+c-1;
        let dest = *dest;

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::C, carry);
            self.set_flag(Flag::Z, dest == 0);
            self.set_flag(Flag::N, dest&0x80000000 != 0);
        }
    }

    #[inline]
    pub fn ARM_RSC(&mut self, bus: &mut Bus, instr: u32) {
        let op1 = self.register[((instr >> 16)&0xF) as usize];
        let (op2, carry) = self.ARM_get_2nd_operand(instr);
        let dest = &mut self.register[((instr >> 12)&0xF) as usize];
        let c = (self.cpsr&Flag::C != 0) as u32;
        *dest = op2-op1+c-1;
        let dest = *dest;

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::C, carry);
            self.set_flag(Flag::Z, dest == 0);
            self.set_flag(Flag::N, dest&0x80000000 != 0);
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
        let dest = &mut self.register[((instr >> 12)&0xF) as usize];
        *dest = op1|op2;
        let dest = *dest;

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::C, carry);
            self.set_flag(Flag::Z, dest == 0);
            self.set_flag(Flag::N, dest&0x80000000 != 0);
        }
    }

    #[inline]
    pub fn ARM_MOV(&mut self, bus: &mut Bus, instr: u32) {
        let (op2, carry) = self.ARM_get_2nd_operand(instr);
        self.register[((instr >> 12)&0xF) as usize] = op2;

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
        let dest = &mut self.register[((instr >> 12)&0xF) as usize];
        *dest = op1& !op2;
        let dest = *dest;

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::C, carry);
            self.set_flag(Flag::Z, dest == 0);
            self.set_flag(Flag::N, dest&0x80000000 != 0);
        }
    }

    #[inline]
    pub fn ARM_MVN(&mut self, bus: &mut Bus, instr: u32) {
        let (op2, carry) = self.ARM_get_2nd_operand(instr);
        self.register[((instr >> 12)&0xF) as usize] = !op2;

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::C, carry);
            self.set_flag(Flag::Z, !op2 == 0);
            self.set_flag(Flag::N, !op2 &0x80000000 != 0);
        }
    }
}