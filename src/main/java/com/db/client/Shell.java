package com.db.client;

import java.util.Scanner;

public class Shell {
    private Client client;

    public Shell(Client client) {
        this.client = client;
    }

    public void run() {
        Scanner sc = new Scanner(System.in);
        try {
            while(true) {
                System.out.print(":> ");
                String statStr = sc.nextLine().trim();
                if("exit".equals(statStr) || "quit".equals(statStr)) {
                    break;
                }
                if(statStr.isEmpty()) {
                    continue;
                }
                try {
                    byte[] res = client.execute(statStr.getBytes());
                    System.out.println(new String(res));
                } catch(Exception e) {
                    System.out.println(e.getMessage());
                }

            }
        } finally {
            sc.close();
            client.close();
        }
    }
}
