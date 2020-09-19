use crate::core::{CPU, Bus};
use crate::core::utils::is_bit_set;

impl CPU {
    pub fn ARM_fill_lut(&mut self) {
        for i in 0 ..= 0xFFF {  // 00001111111100000000000011110000  0x0FF000F0
            if i == 0x121 {
                self.lut_arm[i] = CPU::ARM_bx;
                continue;
            }

            if i&0xE00 == 0xA00 {
                self.lut_arm[i] = CPU::ARM_b_bl;
                continue;
            }


            // DATA PROCESSING (btw it should be at the bottom)
            if i&0xFBF == 0x100 {
                self.lut_arm[i] = CPU::ARM_MRS;
                continue;
            }

            if i&0xFBF == 0x120 || i&0xFB0 == 0x320 {
                self.lut_arm[i] = CPU::ARM_MSR;
                continue;
            }

            if i&0xDE0 == 0x000 {
                self.lut_arm[i] = CPU::ARM_AND;
                continue;
            }

            if i&0xDE0 == 0x020 {
                self.lut_arm[i] = CPU::ARM_EOR;
                continue;
            }

            if i&0xDE0 == 0x040 {
                self.lut_arm[i] = CPU::ARM_SUB;
                continue;
            }

            if i&0xDE0 == 0x060 {
                self.lut_arm[i] = CPU::ARM_RSB;
                continue;
            }

            if i&0xDE0 == 0x080 {
                self.lut_arm[i] = CPU::ARM_ADD;
                continue;
            }

            if i&0xDE0 == 0x0A0 {
                self.lut_arm[i] = CPU::ARM_ADC;
                continue;
            }

            if i&0xDE0 == 0x0C0 {
                self.lut_arm[i] = CPU::ARM_SBC;
                continue;
            }

            if i&0xDE0 == 0x0E0 {
                self.lut_arm[i] = CPU::ARM_RSC;
                continue;
            }

            if i&0xDE0 == 0x100 {
                self.lut_arm[i] = CPU::ARM_TST;
                continue;
            }

            if i&0xDE0 == 0x120 {
                self.lut_arm[i] = CPU::ARM_TEQ;
                continue;
            }

            if i&0xDE0 == 0x140 {
                self.lut_arm[i] = CPU::ARM_CMP;
                continue;
            }

            if i&0xDE0 == 0x160 {
                self.lut_arm[i] = CPU::ARM_CMN;
                continue;
            }

            if i&0xDE0 == 0x180 {
                self.lut_arm[i] = CPU::ARM_ORR;
                continue;
            }

            if i&0xDE0 == 0x1A0 {
                self.lut_arm[i] = CPU::ARM_MOV;
                continue;
            }

            if i&0xDE0 == 0x1C0 {
                self.lut_arm[i] = CPU::ARM_BIC;
                continue;
            }

            if i&0xDE0 == 0x1E0 {
                self.lut_arm[i] = CPU::ARM_MVN;
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