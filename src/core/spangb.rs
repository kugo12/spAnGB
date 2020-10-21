#[allow(non_camel_case_types)]
use crate::core::{Bus, CPU};
// use std::time;

pub struct spAnGB {
    pub bus: Bus,
    cpu: CPU
}

impl spAnGB {
    pub fn new() -> Self {
        Self {
            bus: Bus::new(),
            cpu: CPU::new()
        }
    }

    pub fn run(&mut self) {
        self.cpu.init(&mut self.bus);

        // let start = time::Instant::now();
        // for i in 0..=100u64{
        loop {  
            self.cpu.tick(&mut self.bus);
            self.bus.tick();
        }
        // let t = time::Instant::now();
        // println!("{:?}", t-start);
    }
}