use crate::core::{CPU, Bus, Flag};
use crate::core::utils::is_bit_set;

pub const BARREL_SHIFT: [fn(&mut CPU, u32, u32) -> (u32, bool); 5] = [CPU::barrel_shifter_logical_left, CPU::barrel_shifter_logical_right, CPU::barrel_shifter_arithmethic_right, CPU::barrel_shifter_rotate_right, CPU::barrel_shifter_rotate_right_extended];


impl CPU {
    #[inline]
    pub fn barrel_shifter_operand(&mut self, operand: u32, val: u32) -> (u32, bool) {
        let mut shift_type = ((operand&0x6) >> 1) as usize;
        let amount = if is_bit_set(operand, 0) {
            self.register[((operand&0xF0) >> 4) as usize]&0xFF  
        } else {
            let mut imm = (operand&0xF8) >> 3;
            if imm == 0 {
                match shift_type {
                    1 | 2 => imm = 32,
                    3 => shift_type = 4,
                    _ => ()
                }
            }
            imm
        };

        BARREL_SHIFT[shift_type](self, val, amount)
    }

    #[inline]
    pub fn barrel_shifter_logical_left(&mut self, val: u32, amount: u32) -> (u32, bool) {
        if amount > 31 {
            (0, (amount == 32)&&(val&0x1 != 0))
        } else if amount == 0 {
            (val, self.cpsr&Flag::C != 0)
        } else {
            (val<<amount, is_bit_set(val, (32 - amount) as usize))
        }
    }

    #[inline]
    pub fn barrel_shifter_logical_right(&mut self, val: u32, amount: u32) -> (u32, bool) {
        if amount > 31 {
            (0, (amount==32)&&(val&0x80000000 != 0))
        } else if amount == 0 {
            (val, self.cpsr&Flag::C != 0)
        } else {
            (val>>amount, is_bit_set(val, (amount-1) as usize))
        }
    }

    #[inline]
    pub fn barrel_shifter_arithmethic_right(&mut self, val: u32, mut amount: u32) -> (u32, bool) {
        if amount > 31 {
            ((val as i32).wrapping_shr(31) as u32, val&0x80000000 != 0)
        } else {
            let carry = if amount != 0 { 
                is_bit_set(val, (amount - 1) as usize) 
            } else { 
                self.cpsr&Flag::C != 0
            };

            ((val as i32).wrapping_shr(amount) as u32, carry)
        }
    }

    #[inline]
    pub fn barrel_shifter_rotate_right(&mut self, val: u32, mut amount: u32) -> (u32, bool) {
        if amount == 0 {
            (val, self.cpsr&Flag::C != 0)
        } else {
            let tmp = val.rotate_right(amount);
            (tmp, tmp&0x80000000 != 0)
        }
    }

    
    #[inline]
    pub fn barrel_shifter_rotate_right_extended(&mut self, val: u32, mut amount: u32) -> (u32, bool) {
        let carry_in = (self.cpsr&Flag::C) << 2;
        let carry_out = val&0x1 != 0;
        return ((val >> 1) | carry_in, carry_out)
    }
}