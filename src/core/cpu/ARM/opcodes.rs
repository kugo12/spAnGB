use crate::core::{CPU, Bus, Flag};
use crate::core::utils::is_bit_set;
use crate::core::cpu::CPU_mode;

impl CPU {
    pub fn ARM_fill_lut(&mut self) {
        for i in 0 ..= 0xFFF {  // 00001111111100000000000011110000  0x0FF000F0
            if i&0xF00 == 0xF00 {
                self.lut_arm[i] = CPU::ARM_SWI;
                continue;
            }

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

            if i&0xFBF == 0x109 {
                self.lut_arm[i] = CPU::ARM_SWP;
                continue;
            }

            if i&0xE10 == 0x810 {
                self.lut_arm[i] = CPU::ARM_LDM;
                continue;
            }

            if i&0xE10 == 0x800 {
                self.lut_arm[i] = CPU::ARM_STM;
                continue;
            }

            if i&0xE09 == 0x009 {
                if is_bit_set(i as u32, 4) { // load
                    self.lut_arm[i] = CPU::ARM_LDRHSB;
                } else { // store
                    self.lut_arm[i] = CPU::ARM_STRHSB;
                }
                continue;
            }

            if i&0xC10 == 0x410 {
                self.lut_arm[i] = CPU::ARM_LDR;
                continue;
            }

            if i&0xC10 == 0x400 {
                self.lut_arm[i] = CPU::ARM_STR;
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
            (((instr&0xFFFFFF) | 0x3F000000) as i32) << 2
        } else {
            ((instr&0xFFFFFF) as i32) << 2
        };
        // let off = ((instr << 8) as i32 >> 6) as u32;

        if is_bit_set(instr, 24) {
            self.register[14] = self.register[15] - 4;
        }

        self.register[15] = self.register[15] + off as u32;
        self.arm_refill_pipeline(bus);
    }

    #[inline]
    pub fn ARM_SWI(&mut self, bus: &mut Bus, instr: u32) {
        self.set_mode(CPU_mode::svc);
        self.register[14] = self.register[15] - 4;
        self.register[15] = 0x08;
        self.arm_refill_pipeline(bus);
        println!("SWI {:x}", instr);
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
        let tmp = rm.wrapping_mul(rs).wrapping_add(rn);
        self.register_write(((instr>>16)&0xF) as usize, tmp, bus);

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
        self.register_write(low, tmp as u32, bus);
        self.register_write(high, (tmp >> 32) as u32, bus);

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
            (((self.register[high] as u64) << 32) | self.register[low] as u64) as i64
        } else {
            0
        };

        let tmp = rm*rs + acc;
        self.register_write(low, tmp as u32, bus);
        self.register_write(high, (tmp >> 32) as u32, bus);

        if is_bit_set(instr, 20) {
            self.set_flag(Flag::Z, tmp == 0);
            self.set_flag(Flag::N, tmp as u64&0x8000000000000000 != 0);
        }
    }

    #[inline]
    pub fn ARM_SWP(&mut self, bus: &mut Bus, instr: u32) {
        let rn = self.register[((instr >> 16)&0xF) as usize];
        let rm = self.register[(instr&0xF) as usize];
        let d = ((instr >> 12)&0xF) as usize;

        if is_bit_set(instr, 22) {  // byte
            self.register_write(d, bus.read8(rn) as u32, bus);
            bus.write8(rn, rm as u8);
        } else {  // word
            self.register_write(d, bus.read32(rn& !3).rotate_right((rn&3) << 3), bus);
            bus.write32(rn& !3, rm);
        }
    }

    #[inline]
    pub fn ARM_LDR(&mut self, bus: &mut Bus, instr: u32) {
        let offset = if is_bit_set(instr, 25) {
            self.barrel_shifter_operand(instr>>4, self.register[(instr&0xF) as usize]).0
        } else {
            instr&0xFFF
        };

        let base = ((instr >> 16)&0xF) as usize;
        let tmp = if is_bit_set(instr, 23) {
            self.register[base] + offset
        } else {
            self.register[base] - offset
        };

        if is_bit_set(instr, 24) { // pre
            let v = if is_bit_set(instr, 22) { // byte
                bus.read8(tmp) as u32
            } else {  // word
                bus.read32(tmp& !3).rotate_right((tmp&3) << 3)
            };

            if is_bit_set(instr, 21) {
                self.register_write(base, tmp, bus);
            }
            self.register_write(((instr >> 12)&0xF) as usize, v, bus);
        } else {
            let v = if is_bit_set(instr, 22) { // byte
                bus.read8(self.register[base]) as u32
            } else {  // word
                bus.read32(self.register[base]).rotate_right((self.register[base]&3) << 3)
            };
            self.register_write(base, tmp, bus);
            self.register_write(((instr >> 12)&0xF) as usize, v, bus);
        }
    }
    
    #[inline]
    pub fn ARM_STR(&mut self, bus: &mut Bus, instr: u32) {
        self.register[15] += 4;
        let offset = if is_bit_set(instr, 25) {
            self.barrel_shifter_operand(instr, self.register[(instr&0xF) as usize]).0
        } else {
            instr&0xFFF
        };

        let base = ((instr >> 16)&0xF) as usize;
        let tmp = if is_bit_set(instr, 23) {
            self.register[base] + offset
        } else {
            self.register[base] - offset
        };

        let src = self.register[((instr >> 12)&0xF) as usize];
        if is_bit_set(instr, 24) { // pre
            if is_bit_set(instr, 22) { // byte
                bus.write8(tmp, src as u8);
            } else {  // word
                bus.write32(tmp, src);
            }
            self.register[15] -= 4;
            if is_bit_set(instr, 21) {
                self.register_write(base, tmp, bus);
            }
        } else {
            if is_bit_set(instr, 22) { // byte
                bus.write8(self.register[base], src as u8);
            } else {  // word
                bus.write32(self.register[base], src);
            }
            self.register[15] -= 4;
            self.register_write(base, tmp, bus);
        }
    }

    #[inline]
    pub fn ARM_LDRHSB(&mut self, bus: &mut Bus, instr: u32) { // LDRH LDRSH LDRB LDRSB
        let offset = if is_bit_set(instr, 22) {
            ((instr >> 4)&0xF0) | (instr&0xF)
        } else {
            self.register[(instr&0xF) as usize]
        };

        let base = ((instr >> 16)&0xF) as usize;
        let tmp = if is_bit_set(instr, 23) {
            self.register[base] + offset
        } else {
            self.register[base] - offset
        };

        let addr = if is_bit_set(instr, 24) {
            tmp
        } else {
            self.register[base]
        };
        
        let v = match (instr>>5)&0x3 {
            //0 => bus.read8(addr) as u32, // u8
            1 => (bus.read16(addr& !1) as u32).rotate_right((addr&1) << 3), // u16
            2 => bus.read8(addr) as i8 as i32 as u32, // i8
            3 => (bus.read16(addr) as i16 as i32).wrapping_shr((addr&0x1) << 3) as u32, // i16
            _ => unreachable!()
        };

        if is_bit_set(instr, 21) || !is_bit_set(instr, 24) {
            self.register_write(base, tmp, bus);
        }
        self.register_write(((instr >> 12)&0xF) as usize, v, bus);
    }

    #[inline]
    pub fn ARM_STRHSB(&mut self, bus: &mut Bus, instr: u32) { // STRH STRSH STRB STRSB
        let offset = if is_bit_set(instr, 22) {
            ((instr >> 4)&0xF0) | (instr&0xF)
        } else {
            self.register[(instr&0xF) as usize]
        };

        let base = ((instr >> 16)&0xF) as usize;
        let tmp = if is_bit_set(instr, 23) {
            self.register[base] + offset
        } else {
            self.register[base] - offset
        };
        let src = self.register[((instr >> 12)&0xF) as usize];

        if is_bit_set(instr, 24) { // pre
            match (instr>>5)&0x3 {
                0 => bus.write8(tmp, src as u8),
                1 => bus.write16(tmp, src as u16),
                2 => bus.write8(tmp, src as i32 as i8 as u8),
                3 => bus.write16(tmp, src as i32 as i16 as u16),
                _ => unreachable!()
            };
            if is_bit_set(instr, 21) {
                self.register_write(base, tmp, bus);
            }
        } else {
            match (instr>>5)&0x3 {
                0 => bus.write8(self.register[base], src as u8),
                1 => bus.write16(self.register[base], src as u16),
                2 => bus.write8(self.register[base], src as i32 as i8 as u8),
                3 => bus.write16(self.register[base], src as i32 as i16 as u16),
                _ => unreachable!()
            };
            self.register_write(base, tmp, bus);
        }
    }
}