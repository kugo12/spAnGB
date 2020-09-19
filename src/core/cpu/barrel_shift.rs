use crate::core::{CPU, Bus, Flag};
use crate::core::utils::is_bit_set;

pub const BARREL_SHIFT: [fn(&mut CPU, u32, u32) -> (u32, bool); 4] = [CPU::barrel_shifter_logical_left, CPU::barrel_shifter_logical_right, CPU::barrel_shifter_arithmethic_right, CPU::barrel_shifter_rotate_right];


impl CPU {
    #[inline]
    pub fn barrel_shifter_operand(&mut self, operand: u32, val: u32) -> (u32, bool) {
        let amount = if is_bit_set(operand, 4) {
            self.register[((operand&0xF00) >> 8) as usize]&0xF
        } else {
            (operand&0xF80) >> 7
        };
        
        BARREL_SHIFT[((operand&0x60) >> 5) as usize](self, val, amount)
    }

    #[inline]
    pub fn barrel_shifter_logical_left(&mut self, val: u32, amount: u32) -> (u32, bool) {
        let carry = if amount > 32 {
            false
        } else if amount == 0 {
            return (val, self.cpsr&Flag::C != 0)
        } else {
            is_bit_set(val, (32 - amount) as usize)
        };

        (val<<amount, carry)
    }

    #[inline]
    pub fn barrel_shifter_logical_right(&mut self, val: u32, amount: u32) -> (u32, bool) {
        let carry = if amount > 32 {
            false
        } else if amount == 0 {
            return (val, self.cpsr&Flag::C != 0)
        } else {
            is_bit_set(val, (amount-1) as usize)
        };

        (val>>amount, carry)
    }

    #[inline]
    pub fn barrel_shifter_arithmethic_right(&mut self, val: u32, mut amount: u32) -> (u32, bool) {
        if amount > 32 || amount == 0 {
            amount = 32;
        }
        let carry = is_bit_set(val, (amount - 1) as usize);

        ((val as i32 >> amount as i32) as u32, carry)
    }

    #[inline]
    pub fn barrel_shifter_rotate_right(&mut self, val: u32, mut amount: u32) -> (u32, bool) {
        if amount == 0 {  // RR extended
            let carry_in = (self.cpsr&Flag::C) << 2;
            let carry_out = val&0x1 != 0;
            return ((val >> 1) | carry_in, carry_out)
        } else {
            let carry = is_bit_set(val, (amount%31) as usize); // idk if this is enough
            return (val.rotate_right(amount), carry)
        }
    }
}