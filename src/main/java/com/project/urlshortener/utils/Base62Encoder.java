package com.project.urlshortener.utils;

import com.project.urlshortener.constants.Constants;
import org.springframework.stereotype.Component;

@Component
public class Base62Encoder {

    public String encode(long id) {
        if (id == 0)
            return String.valueOf(Constants.BASE62.charAt(0));

        StringBuilder sb = new StringBuilder();

        while (id > 0) {
            int remainder = (int) (id % 62);
            sb.append(Constants.BASE62.charAt(remainder));
            id /= 62;
        }

        return sb.reverse().toString();
    }

    public long decode(String shortCode) {
        long id = 0;
        for (int i = 0; i < shortCode.length(); i++) {
            char c = shortCode.charAt(i);
            int value = Constants.BASE62.indexOf(c);

            if (value == -1) {
                throw new IllegalArgumentException("Invalid character in shortCode");
            }
            id = id * 62 + value;
        }
        return id;
    }
}
