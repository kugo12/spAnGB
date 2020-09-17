use crate::core::{CPU, Bus};
use crate::core::utils::is_bit_set;

impl CPU {
    pub fn ARM_fill_lut(&mut self) {
        for i in 0 ..= 0xFFF {  // 00001111111100000000000011110000  0x0FF000F0
            if i == 0x121 {
                self.lut_arm[i] = CPU::ARM_bx;
                continue;
            }

            if i&0xA00 == 0xA00 {
                self.lut_arm[i] = CPU::ARM_b_bl;
                continue;
            }
        }
    }

    #[inline]
    pub fn ARM_bx(&mut self, bus: &mut Bus, instr: u32) {
        let rn = self.register[(instr&0xF) as usize];
        self.register[15] = rn;
        self.switch_state(bus, rn);
    }

    #[inline]
    pub fn ARM_b_bl(&mut self, bus: &mut Bus, instr: u32) {
        let off = if is_bit_set(instr, 23) {
            (((instr&0xFFFFFF) << 2) | 0x80000000) as i32
        } else {
            ((instr&0xFFFFFF) << 2) as i32
        };

        if is_bit_set(instr, 24) {
            self.register[14] = self.register[15] - 4;
        }

        self.register[15] = (self.register[15] as i32 + off) as u32;
    }
}