use std::path::Path;
use std::fs::File;
use std::io::prelude::*;

pub struct Cartridge {
    pub rom: Vec<u8>,
    pub title: String,
}

impl Cartridge {
    pub fn new() -> Self {
        Self {
            rom: vec![],
            title: String::new()
        }
    }

    pub fn insert(&mut self, p: &Path) {
        let mut data: Vec<u8> = vec![];
        {
            let mut file = File::open(p).unwrap();
            file.read_to_end(&mut data).unwrap();
        }

        if self.calculate_header_checksum(&data) != data[0xBD] {
            panic!("Wrong ROM checksum");
        }

        self.title = String::from_utf8(data[0xA0..0xAC].to_vec()).unwrap();
        self.rom = data;

        println!("{}", self.title);
    }

    fn calculate_header_checksum(&self, data: &Vec<u8>) -> u8 {
        let mut sum: u8 = 0;
        for i in 0xA0 ..= 0xBC {
            sum = sum.wrapping_sub(data[i]);
        }
        sum.wrapping_sub(0x19)
    }
}