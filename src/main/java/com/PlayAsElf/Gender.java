package com.PlayAsElf;

public enum Gender {
    MALE,
    FEMALE;

    @Override
    public String toString()
    {
        switch(this)
        {
            case MALE:
                return "Male";
            case FEMALE:
                return "Female";
            default:
                return "";
        }
    }
}
