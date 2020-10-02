use crate::core::{CPU, Bus};
use crate::core::utils::is_bit_set;
use crate::core::cpu::CPU_mode;


fn reglist(mut instr: u32) -> [(bool, usize); 16] {
    let mut r = [(false, 0); 16];
    for i in 0..16 {
        r[i] = (instr%2 == 1, i);
        instr /= 2;
    }
    r
}

impl CPU {
    pub fn ARM_STM(&mut self, bus: &mut Bus, instr: u32) {
        let pre = is_bit_set(instr, 24);
        let up = is_bit_set(instr, 23);
        let psr_force_user = is_bit_set(instr, 22);
        let mode = self.mode;
        
        let base = ((instr >> 16)&0xF) as usize;
        let mut regs = reglist(instr);
        let mut addr = self.register[base];

        if !up {
            regs.reverse();
        }

        if pre {
            if up {
                addr += 4;
            } else {
                addr -= 4;
            }
        }

        if psr_force_user {
            self.set_mode(CPU_mode::usr);
        }

        for (yes, reg) in regs.iter() {
            if *yes {
                bus.write32(addr, self.register[*reg]);
                if up {
                    addr += 4;
                } else {
                    addr -= 4;
                }
            }
        }

        if psr_force_user {
            self.set_mode(mode);
        }

        if is_bit_set(instr, 21) { // writeback
            if pre {
                if up {
                    addr -= 4;
                } else {
                    addr += 4;
                }
            }
            self.register[base] = addr;
        }
    }

    pub fn ARM_LDM(&mut self, bus: &mut Bus, instr: u32) {
        let pre = is_bit_set(instr, 24);
        let up = is_bit_set(instr, 23);
        let psr_force_user = is_bit_set(instr, 22);
        let mode = self.mode;
        
        let base = ((instr >> 16)&0xF) as usize;
        let mut regs = reglist(instr);
        let mut addr = self.register[base];

        if psr_force_user {
            if regs[15].0 {
                self.cpsr = match mode {
                    CPU_mode::sys | CPU_mode::usr => self.cpsr,
                    CPU_mode::fiq => self.fiq_spsr,
                    _ => self.bank_reg[mode as usize][2]
                };
            }
            self.set_mode(CPU_mode::usr);
        }

        if !up {
            regs.reverse();
        }

        if pre {
            if up {
                addr += 4;
            } else {
                addr -= 4;
            }
        }

        for (yes, reg) in regs.iter() {
            if *yes {
                self.register[*reg] = bus.read32(addr);
                if up {
                    addr += 4;
                } else {
                    addr -= 4;
                }
            }
        }

        if psr_force_user {
            self.set_mode(mode);
        }

        if is_bit_set(instr, 21) { // writeback
            if pre {
                if up {
                    addr -= 4;
                } else {
                    addr += 4;
                }
            }
            self.register[base] = addr;
        }
    }
}