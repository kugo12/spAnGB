use crate::core::{CPU, Bus};
use crate::core::utils::is_bit_set;
use crate::core::cpu::CPU_mode;


fn reglist(mut instr: u32) -> ([(bool, usize); 16], i8) {
    let mut r = [(false, 0); 16];
    let mut first = -1;
    for i in 0..16 {
        r[i] = (instr%2 == 1, i);
        if first == -1 && r[i].0 {
            first = i as i8;
        }
        instr /= 2;
    }
    (r, first)
}

impl CPU {
    pub fn ARM_STM(&mut self, bus: &mut Bus, instr: u32) {
        let pre = is_bit_set(instr, 24);
        let up = is_bit_set(instr, 23);
        let psr_force_user = is_bit_set(instr, 22);
        let mode = self.mode;

        self.register[15] += 4;
        
        let base = ((instr >> 16)&0xF) as usize;
        let (mut regs, first) = reglist(instr);
        let mut addr = self.register[base];
        let mut base_in_rlist: (bool, u32) = (regs[base].0, 0);

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

        if first == -1 { // empty rlist
            bus.write32(addr, self.register[15]);
            if up {
                addr += 0x40;
            } else {
                addr -= 0x40;
            }
        } else {
            for (yes, reg) in regs.iter() {
                if *yes {
                    bus.write32(addr, self.register[*reg]);
                    if *reg == base {
                        base_in_rlist.1 = addr;
                    }

                    if up {
                        addr += 4;
                    } else {
                        addr -= 4;
                    }
                }
            }
        }

        if psr_force_user {
            self.set_mode(mode);
        }

        self.register[15] -= 4;
        if is_bit_set(instr, 21) { // writeback
            if pre {
                if up {
                    addr -= 4;
                } else {
                    addr += 4;
                }
            }
            
            if base_in_rlist.0 && first != base as i8 {
                bus.write32(base_in_rlist.1, addr);
            }
            self.register_write(base, addr, bus);
        }
    }

    pub fn ARM_LDM(&mut self, bus: &mut Bus, instr: u32) {
        let pre = is_bit_set(instr, 24);
        let up = is_bit_set(instr, 23);
        let psr_force_user = is_bit_set(instr, 22);
        let mode = self.mode;
        
        let base = ((instr >> 16)&0xF) as usize;
        let (mut regs, first) = reglist(instr);
        let mut addr = self.register[base];
        let base_in_rlist = regs[base].0;

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

        if first == -1 {
            self.register_write(15, bus.read32(addr), bus);
            if up {
                addr += 0x40;
            } else {
                addr -= 0x40;
            }
        } else {
            for (yes, reg) in regs.iter() {
                if *yes {
                    self.register_write(*reg, bus.read32(addr), bus);
                    if up {
                        addr += 4;
                    } else {
                        addr -= 4;
                    }
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
            if !base_in_rlist {
                self.register_write(base, addr, bus);
            }
        }
    }
}