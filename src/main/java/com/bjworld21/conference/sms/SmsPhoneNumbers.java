package com.bjworld21.conference.sms;

public final class SmsPhoneNumbers {
    private SmsPhoneNumbers() {}
    public static String recipient(String value) {
        if (value == null || !value.trim().matches("[+0-9().\\s-]+")) return null;
        String number = value.trim().replaceAll("[().\\s-]", "");
        if (number.startsWith("0082")) number = "+82" + number.substring(4);
        if (number.startsWith("82") && number.length() >= 11) number = "+" + number;
        if (number.startsWith("+82")) {
            String local = number.substring(3);
            number = local.startsWith("0") ? local : "0" + local;
        }
        if (number.matches("010[0-9]{8}|01[16789][0-9]{7,8}")) return "+82" + number.substring(1);
        if (number.startsWith("+82") || number.startsWith("0")) return null;
        return number.matches("\\+[1-9][0-9]{7,14}") ? number : null;
    }
    public static String sender(String value) {
        if (value == null || !value.trim().matches("[0-9()\\s-]+")) return null;
        String number = value.replaceAll("[()\\s-]", "");
        return number.matches("[0-9]{8,11}") ? number : null;
    }
}
