use crate::core::{CPU, Bus, Flag};
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

            if i&0xFCF == 0x009 {
                self.lut_arm[i] = CPU::ARM_MUL_MLA;
                continue;
            }

            if i&0xF8F == 0x089 {
                if is_bit_set(i as u32, 6) {
                    self.lut_arm[i] = CPU::ARM_SMULL_SMLAL;
                } else {
                    self.lut_arm[i] = CPU::ARM_UMULL_UMLAL;
                }
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

    #[inline]
    pub fn ARM_MUL_MLA(&mut self, bus: &mut Bus, instr: u32) {
        let rn = if is_bit_set(instr, 21) {
            self.register[((instr>>12)&0xF) as usize]
        } else {
            0
        };

        let rs = self.register[((instr>>8)&0xF) as usize];
        let rm = self.register[(instr&0xF) as usize];
        let tmp = rm*rs + rn;
        self.register[((instr>>16)&0xF) as usize] = tmp;

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::Z, tmp == 0);
            self.set_flag(Flag::N, tmp&0x80000000 != 0);
        }
    }

    #[inline]
    pub fn ARM_UMULL_UMLAL(&mut self, bus: &mut Bus, instr: u32) {
        let rm = self.register[(instr&0xF) as usize] as u64;
        let rs = self.register[((instr>>8)&0xF) as usize] as u64;

        let low = ((instr>>12)&0xF) as usize;
        let high = ((instr>>16)&0xF) as usize;

        let acc = if is_bit_set(instr, 21) {
            ((self.register[high] as u64) << 32) | self.register[low] as u64
        } else {
            0
        };

        let tmp = rm*rs + acc;
        self.register[low] = tmp as u32;
        self.register[high] = (tmp >> 32) as u32;

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::Z, tmp == 0);
            self.set_flag(Flag::N, tmp&0x8000000000000000 != 0);
        }
    }

    #[inline]
    pub fn ARM_SMULL_SMLAL(&mut self, bus: &mut Bus, instr: u32) {
        let rm = self.register[(instr&0xF) as usize] as i32 as i64;
        let rs = self.register[((instr>>8)&0xF) as usize] as i32 as i64;

        let low = ((instr>>12)&0xF) as usize;
        let high = ((instr>>16)&0xF) as usize;

        let acc = if is_bit_set(instr, 21) {
            ((self.register[high] as i32 as i64) << 32) | self.register[low] as i64
        } else {
            0
        };

        let tmp = rm*rs + acc;
        self.register[low] = tmp as u32;
        self.register[high] = (tmp as u64 >> 32) as u32;

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::Z, tmp == 0);
            self.set_flag(Flag::N, tmp as u64&0x8000000000000000 != 0);
        }
    }
}