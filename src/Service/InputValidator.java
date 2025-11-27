package Service;

import Model.RoleType;

import java.util.Scanner;

public class InputValidator {

    static Scanner scanner = new Scanner(System.in);
    public static String getInput(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    public static String getValidEmail() {
        while (true) {
            String email = getInput("Enter Email: ").trim();
            if (email.matches("^[\\w.-]+@university\\.edu$")) {
                return email;
            }
            System.out.println("Invalid email. Only @university.edu addresses are allowed.");
        }
    }

    public static RoleType getValidRole() {
        while (true) {
            System.out.print("Enter Role (STRATEGIST, ATTACKER, DEFENDER, SUPPORTER, COORDINATOR): ");
            try {
                String input = scanner.nextLine().trim().toUpperCase();
                return RoleType.valueOf(input);
            } catch (IllegalArgumentException ex) {
                System.out.println("Invalid role.");
            }
        }
    }

    public static int getUserInput(String prompt, int min, int max) {
        return getUserInput(prompt, min, max, false);
    }

    public static int getUserInput(String prompt, int min, int max, boolean allowZero) {
        while (true) {
            System.out.print(prompt);
            try {
                int value = Integer.parseInt(scanner.nextLine().trim());
                if (allowZero) min = 0;
                if (value >= min && value <= max) return value;
                System.out.println("Enter a number between " + min + " and " + max);
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Must be a number.");
            }
        }
    }
}
