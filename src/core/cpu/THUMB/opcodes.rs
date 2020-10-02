use crate::core::{CPU, Bus, Flag};
use crate::core::utils::is_bit_set;
use crate::core::cpu::{CPU_state, CPU_mode};


impl CPU {
    pub fn THUMB_fill_lut(&mut self) {
        for i in 0..1024 {  // 1111111111000000  0xFFC0  -> 0x3FF
            if i&0x3FC == 0x37C {
                self.lut_thumb[i] = CPU::THUMB_SWI;
                continue;
            }

            if i&0x3D8 == 0x2D0 {
                self.lut_thumb[i] = if i&0x020 != 0 {
                    CPU::THUMB_POP
                } else {
                    CPU::THUMB_PUSH
                };

                continue;
            }

            if i&0x3C0 == 0x300 {
                self.lut_thumb[i] = if i&0x020 != 0 {
                    CPU::THUMB_LDMIA
                } else {
                    CPU::THUMB_STMIA
                };
                
                continue;
            }

            if i&0x3C0 == 0x340 {
                self.lut_thumb[i] = CPU::THUMB_COND_BRANCH;
                continue;
            }

            if i&0x3E0 == 0x380 {
                self.lut_thumb[i] = CPU::THUMB_BRANCH;
                continue;
            }

            if i&0x3C0 == 0x3C0 {
                self.lut_thumb[i] = CPU::THUMB_LONG_BRANCH_LINK;
                continue;
            }

            if i&0x3F0 == 0x100 {
                self.lut_thumb[i] = [
                    CPU::THUMB_AND,
                    CPU::THUMB_EOR,
                    CPU::THUMB_LSL,
                    CPU::THUMB_LSR,
                    CPU::THUMB_ASR,
                    CPU::THUMB_ADC,
                    CPU::THUMB_SBC,
                    CPU::THUMB_ROR,
                    CPU::THUMB_TST,
                    CPU::THUMB_NEG,
                    CPU::THUMB_CMP,
                    CPU::THUMB_CMN,
                    CPU::THUMB_ORR,
                    CPU::THUMB_MUL,
                    CPU::THUMB_BIC,
                    CPU::THUMB_MVN
                ][((i >> 6)&0xF)];
                continue;
            }

            if i&0x3F0 == 0x110 {
                self.lut_thumb[i] = match (i >> 2)&0x3 {
                    0 => CPU::THUMB_HILO_ADD,
                    1 => CPU::THUMB_HILO_CMP,
                    2 => CPU::THUMB_HILO_MOV,
                    3 => CPU::THUMB_HILO_BX,
                    _ => unreachable!(),
                };
                continue;
            }

            if i&0x3C8 == 0x148 {
                self.lut_thumb[i] = match (i>>4)&0x3 {
                    0 => CPU::THUMB_STRH,
                    1 => CPU::THUMB_LDSB,
                    2 => CPU::THUMB_LDRH,
                    3 => CPU::THUMB_LDSH,
                    _ => unreachable!()
                };
                continue;
            }

            if i&0x380 == 0x180 {
                self.lut_thumb[i] = CPU::THUMB_LDR_STR_IMM_OFF;
                continue;
            }

            if i&0x3C0 == 0x200 {
                self.lut_thumb[i] = CPU::THUMB_LDRH_STRH_IMM_OFF;
                continue;
            }

            if i&0x3E0 == 0x120 {
                self.lut_thumb[i] = CPU::THUMB_LDR_PCREL_IMM;
                continue;
            }

            if i&0x3C8 == 0x140 {
                self.lut_thumb[i] = CPU::THUMB_LDSTR_REG_OFF;
                continue;
            }

            if i&0x3C0 == 0x240 {
                self.lut_thumb[i] = CPU::THUMB_LDR_STR_SP_REL;
                continue;
            }

            if i&0x3FC == 0x2C0 {
                self.lut_thumb[i] = CPU::THUMB_SP_OFF;
                continue;
            }

            if i&0x3C0 == 0x280 {
                self.lut_thumb[i] = CPU::THUMB_LD_PC_SP;
                continue;
            }

            if i&0x3E0 == 0x080 {
                self.lut_thumb[i] = CPU::THUMB_MOV;
                continue;
            }

            if i&0x3E0 == 0x0A0 {
                self.lut_thumb[i] = CPU::THUMB_CMPIMM;
                continue;
            }

            if i&0x3E0 == 0x0C0 {
                self.lut_thumb[i] = CPU::THUMB_ADD_SUB;
                continue;
            }

            if i&0x380 == 0x000 {
                self.lut_thumb[i] = CPU::THUMB_MOVS;
                continue;
            }
        }
    }

    pub fn THUMB_MOVS(&mut self, bus: &mut Bus, instr: u16) {
        let op = ((instr >> 11)&0x3) as usize;
        let rs = self.register[((instr>>3)&0x7) as usize];
        let off = ((instr >> 6)&0x3F) as u32;

        let tmp = [
            CPU::barrel_shifter_logical_left,
            CPU::barrel_shifter_logical_right,
            CPU::barrel_shifter_arithmethic_right
        ][op](self, rs, off);

        self.register[(instr&0x7) as usize] = tmp.0;
        self.set_flag(Flag::C, tmp.1);
    }

    pub fn THUMB_ADD_SUB(&mut self, bus: &mut Bus, instr: u16) {
        let rn = if is_bit_set(instr as u32, 10) {
            ((instr >> 6)&0x7) as u32
        } else {
            self.register[((instr >> 6)&0x7) as usize]
        };
        let src = self.register[((instr >> 3)&0x7) as usize];

        let tmp = if is_bit_set(instr as u32, 9) {
            src - rn
        } else {
            src + rn
        };
        self.register[(instr&0x7) as usize] = tmp;

        self.set_flag(Flag::N, tmp&0x80000000 != 0);
    }

    pub fn THUMB_MOV(&mut self, bus: &mut Bus, instr: u16) {
        let imm = instr&0xFF;
        self.register[((instr >> 8)&0x7) as usize] = imm as u32;
    }

    pub fn THUMB_CMPIMM(&mut self, bus: &mut Bus, instr: u16) {
        let imm = instr&0xFF;
        
        let op1 = self.register[((instr >> 8)&0x7) as usize];
        let tmp = op1-imm as u32;

        self.set_flag(Flag::Z, tmp == 0);
        self.set_flag(Flag::N, tmp&0x80000000 != 0);
    }

    pub fn THUMB_LDR_PCREL_IMM(&mut self, bus: &mut Bus, instr: u16) {
        let imm = (instr&0xFF) << 2;
        self.register[((instr >> 8)&0x7) as usize] = bus.read32(self.register[15] + imm as u32);
    }

    pub fn THUMB_LDSTR_REG_OFF(&mut self, bus: &mut Bus, instr: u16) {
        let off = self.register[((instr >> 6)&0x7) as usize] + self.register[((instr >> 3)&0x7) as usize];
        let rd = (instr&0x7) as usize;

        if is_bit_set(instr as u32, 11) {  // load
            if is_bit_set(instr as u32, 10) {  // byte
                self.register[rd] = bus.read8(off) as u32;
            } else {  // word
                self.register[rd] = bus.read32(off);
            }
        } else {  // store
            if is_bit_set(instr as u32, 10) { // byte
                bus.write8(off, self.register[rd] as u8);
            } else {  // word
                bus.write32(off, self.register[rd]);
            }
        }
    }

    pub fn THUMB_STRH(&mut self, bus: &mut Bus, instr: u16) {
        let off = self.register[((instr >> 6)&0x7) as usize] + self.register[((instr >> 3)&0x7) as usize];
        bus.write16(off, self.register[(instr&0x7) as usize] as u16);
    }

    pub fn THUMB_LDRH(&mut self, bus: &mut Bus, instr: u16) {
        let off = self.register[((instr >> 6)&0x7) as usize] + self.register[((instr >> 3)&0x7) as usize];
        self.register[(instr&0x7) as usize] = bus.read16(off) as u32;
    }

    pub fn THUMB_LDSB(&mut self, bus: &mut Bus, instr: u16) {
        let off = self.register[((instr >> 6)&0x7) as usize] + self.register[((instr >> 3)&0x7) as usize];
        self.register[(instr&0x7) as usize] = bus.read8(off) as i8 as i32 as u32;
    }

    pub fn THUMB_LDSH(&mut self, bus: &mut Bus, instr: u16) {
        let off = self.register[((instr >> 6)&0x7) as usize] + self.register[((instr >> 3)&0x7) as usize];
        self.register[(instr&0x7) as usize] = bus.read16(off) as i16 as i32 as u32;
    }

    pub fn THUMB_LDR_STR_IMM_OFF(&mut self, bus: &mut Bus, instr: u16) {
        let addr = {
            let off = (instr >> 4)&0x7C;
            let rb = self.register[((instr >> 3)&0x7) as usize];
            rb + off as u32
        };
        let rd = (instr&0x7) as usize;

        if is_bit_set(instr as u32, 11) { // load
            if is_bit_set(instr as u32, 12) { // byte
                self.register[rd] = bus.read8(addr) as u32;
            } else {
                self.register[rd] = bus.read32(addr);
            }
        } else {
            if is_bit_set(instr as u32, 12) {
                bus.write8(addr, self.register[rd] as u8);
            } else {
                bus.write32(addr, self.register[rd]);
            }
        }
    }

    pub fn THUMB_LDRH_STRH_IMM_OFF(&mut self, bus: &mut Bus, instr: u16) {
        let addr = {
            let off = (instr >> 4)&0x7C;
            let rb = self.register[((instr >> 3)&0x7) as usize];
            rb + off as u32
        };
        let rd = (instr&0x7) as usize;

        if is_bit_set(instr as u32, 11) { // load
            self.register[rd] = bus.read16(addr) as u32;
        } else {
            bus.write16(addr, self.register[rd] as u16);
        }
    }

    pub fn THUMB_LDR_STR_SP_REL(&mut self, bus: &mut Bus, instr: u16) {
        let addr = {
            let rb = self.register[13];
            let off = ((instr&0xFF) << 2) as u32;
            rb + off
        };
        let rd = ((instr >> 8)&0x7) as usize;

        if is_bit_set(instr as u32, 11) {  // load
            self.register[rd] = bus.read32(addr);
        } else {
            bus.write32(addr, self.register[rd]);
        }
    }

    pub fn THUMB_LD_PC_SP(&mut self, bus: &mut Bus, instr: u16) {
        let off = ((instr&0xFF) << 2) as u32;
        let src = if is_bit_set(instr as u32, 11) {
            self.register[13]
        } else {
            self.register[15]
        };

        self.register[((instr >> 8)&0x7) as usize] = off+src;
    }

    pub fn THUMB_SP_OFF(&mut self, bus: &mut Bus, instr: u16) {
        let off = (instr&0x7F) as u32;

        if is_bit_set(instr as u32, 7) {  // off neg
            self.register[13] -= off;
        } else {
            self.register[13] += off;
        }
    }

    pub fn THUMB_COND_BRANCH(&mut self, bus: &mut Bus, instr: u16) {
        if self.is_condition((instr >> 8) as u8) {
            let off = (instr&0xFF) as i8 as i32;
            self.register[15] = (self.register[15] as i32 + off) as u32;
            self.flush_pipeline();
            self.thumb_fill_pipeline(bus);
        }
    }

    pub fn THUMB_BRANCH(&mut self, bus: &mut Bus, instr: u16) {
        let addr = (self.register[15] - 4) + ((instr << 1)&0xFFF) as u32;
        self.register[15] = addr;
        self.flush_pipeline();
        self.thumb_fill_pipeline(bus);
    }

    pub fn THUMB_LONG_BRANCH_LINK(&mut self, bus: &mut Bus, instr: u16) {
        if is_bit_set(instr as u32, 11) { // low
            let off = ((instr << 1)&0xFFF) as u32;
            let tmp = self.register[14] + off;
            self.register[14] = self.register[15] - 4;
            self.register[15] = tmp;
            self.flush_pipeline();
            self.thumb_fill_pipeline(bus);
        } else { // high
            let off = (instr as u32&0x7FF) << 12;
            self.register[14] = self.register[15] + off;
        }
    }

    pub fn THUMB_SWI(&mut self, bus: &mut Bus, instr: u16) {
        self.set_mode(CPU_mode::svc);
        self.register[15] = 0x8;
        self.switch_state(bus, 0);
    }

    pub fn THUMB_PUSH(&mut self, bus: &mut Bus, instr: u16) {
        let mut sp_update = 0;
        let mut rlist = instr&0xFF;

        for i in 0..=7 {
            if rlist%2 == 1 {
                bus.write32(self.register[13]-sp_update, self.register[i]);

                sp_update += 4;
            }
            rlist >>= 1;
        }
        
        if is_bit_set(instr as u32, 8) {
            bus.write32(self.register[13]-sp_update, self.register[14]);
            sp_update += 4;
        }

        self.register[13] -= sp_update;
    }

    pub fn THUMB_POP(&mut self, bus: &mut Bus, instr: u16) {
        let mut sp_update = 0;
        let mut rlist = instr&0xFF;

        for i in 0..=7 {
            if rlist%2 == 1 {
                self.register[i] = bus.read32(self.register[13] + sp_update);
                sp_update += 4;
            }
            rlist >>= 1;
        }

        if is_bit_set(instr as u32, 8) {
            self.register[15] = bus.read32(self.register[13]+sp_update);
            sp_update += 4;

            self.flush_pipeline();
            self.thumb_fill_pipeline(bus);
        }

        self.register[13] += sp_update;
    }

    pub fn THUMB_STMIA(&mut self, bus: &mut Bus, instr: u16) {
        let mut update = 0;
        let mut rlist = instr&0xFF;
        let mut rb = ((instr >> 8)&0x7) as usize;
        let mut addr = self.register[rb];

        for i in 0..=7 {
            if rlist%2 == 1 {
                bus.write32(addr+update, self.register[i]);
                update += 4;
            }

            rlist >>= 1;
        }

        self.register[rb] += update;
    }
    
    pub fn THUMB_LDMIA(&mut self, bus: &mut Bus, instr: u16) {
        let mut update = 0;
        let mut rlist = instr&0xFF;
        let mut rb = ((instr >> 8)&0x7) as usize;
        let mut addr = self.register[rb];

        for i in 0..=7 {
            if rlist%2 == 1 {
                self.register[i] = bus.read32(addr - update);
                update += 4;
            }

            rlist >>= 1;
        }

        self.register[rb] -= update;
    }
}