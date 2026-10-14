package com.nineteenhome.lofimusic;

import java.util.Calendar;

public class LunarCalendar {
    // 1900-2100 年农历数据
    private final static int[] lunarInfo = new int[]{
            0x04bd8,0x04ae0,0x0a570,0x054d5,0x0d260,0x0d950,0x16554,0x056a0,0x09ad0,0x055d2,
            0x04ae0,0x0a5b6,0x0a4d0,0x0d250,0x1d255,0x0b540,0x0d6a0,0x0ada2,0x095b0,0x14977,
            0x04970,0x0a4b0,0x0b4b5,0x06a50,0x06d40,0x1ab54,0x02b60,0x09570,0x052f2,0x04970,
            0x06566,0x0d4a0,0x0ea50,0x06e95,0x05ad0,0x02b60,0x186e3,0x092e0,0x1c8d7,0x0c950,
            0x0d4a0,0x1d8a6,0x0b550,0x056a0,0x1a5b4,0x025d0,0x092d0,0x0d2b2,0x0a950,0x0b557,
            0x06ca0,0x0b550,0x15355,0x04da0,0x0a5b0,0x14573,0x052b0,0x0a9a8,0x0e950,0x06aa0,
            0x0aea6,0x0ab50,0x04b60,0x0aae4,0x0a570,0x05260,0x0f263,0x0d950,0x05b57,0x056a0,
            0x096d0,0x04dd5,0x04ad0,0x0a4d0,0x0d4d4,0x0d250,0x0d558,0x0b540,0x0b6a0,0x195a6,
            0x095b0,0x049b0,0x0a974,0x0a4b0,0x0b27a,0x06a50,0x06d40,0x0af46,0x0ab60,0x09570,
            0x04af5,0x04970,0x064b0,0x074a3,0x0ea50,0x06b58,0x055c0,0x0ab60,0x096d5,0x092e0,
            0x0c960,0x0d954,0x0d4a0,0x0da50,0x07552,0x056a0,0x0abb7,0x025d0,0x092d0,0x0cab5,
            0x0a950,0x0b4a0,0x0baa4,0x0ad50,0x055d9,0x04ba0,0x0a5b0,0x15176,0x052b0,0x0a930,
            0x07954,0x06aa0,0x0ad50,0x05b52,0x04b60,0x0a6e6,0x0a4e0,0x0d260,0x0ea65,0x0d530,
            0x05aa0,0x076a3,0x096d0,0x04afb,0x04ad0,0x0a4d0,0x1d0b6,0x0d250,0x0d520,0x0dd45,
            0x0b5a0,0x056d0,0x055b2,0x049b0,0x0a577,0x0a4b0,0x0aa50,0x1b255,0x06d20,0x0ada0,
            0x14b63,0x09370,0x049f8,0x04970,0x064b0,0x168a6,0x0ea50,0x06b20,0x1a6c4,0x0aae0,
            0x0a2e0,0x0d2e3,0x0c960,0x0d557,0x0d4a0,0x0da50,0x05d55,0x056a0,0x0a6d0,0x055d4,
            0x052d0,0x0a9b8,0x0a950,0x0b4a0,0x0b6a6,0x0ad50,0x055a0,0x0aba4,0x0a5b0,0x052b0,
            0x0b273,0x06930,0x07337,0x06aa0,0x0ad50,0x14b55,0x04b60,0x0a570,0x054e4,0x0d160,
            0x0e968,0x0d520,0x0daa0,0x16aa6,0x056d0,0x04ae0,0x0a9d4,0x0a2d0,0x0d150,0x0f252,
            0x0d520};

    private final static String[] nStr1 = {"","正","二","三","四","五","六","七","八","九","十","冬","腊"};
    private final static String[] Gan = {"甲","乙","丙","丁","戊","己","庚","辛","壬","癸"};
    private final static String[] Zhi = {"子","丑","寅","卯","辰","巳","午","未","申","酉","戌","亥"};
    private final static String[] Animals = {"鼠","牛","虎","兔","龙","蛇","马","羊","猴","鸡","狗","猪"};
    private final static String[] nStr2 = {"初一","初二","初三","初四","初五","初六","初七","初八","初九","初十",
            "十一","十二","十三","十四","十五","十六","十七","十八","十九","二十",
            "廿一","廿二","廿三","廿四","廿五","廿六","廿七","廿八","廿九","三十"};

    private int year;
    private int month;
    private int day;
    private boolean leap;

    public LunarCalendar(Calendar cal) {
        // 创建基准日历（1900年1月31日，农历正月初一）
        Calendar baseDate = Calendar.getInstance();
        baseDate.set(1900, 0, 31);

        // 将传入的日历和基准日历的时分秒毫秒全部归零，消除时间差导致的日期计算错误
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        baseDate.set(Calendar.HOUR_OF_DAY, 0);
        baseDate.set(Calendar.MINUTE, 0);
        baseDate.set(Calendar.SECOND, 0);
        baseDate.set(Calendar.MILLISECOND, 0);

        // 计算目标日期距离基准日的总天数
        long offset = (cal.getTimeInMillis() - baseDate.getTimeInMillis()) / 86400000L;

        int iYear = 1900;
        // 确定农历年份
        for (; iYear < 2101 && offset > 0; iYear++) {
            offset -= lYearDays(iYear);
        }
        if (offset < 0) {
            offset += lYearDays(--iYear);
        }

        this.year = iYear;
        this.leap = false;
        int leapMonth = leapMonth(iYear); // 闰哪个月

        // 确定农历月份和日期
        int iMonth = 1;
        for (; iMonth < 13; iMonth++) {
            int monthDays = monthDays(this.year, iMonth);
            if (offset < monthDays) {
                this.month = iMonth;
                this.day = (int) offset + 1;
                break;
            }
            offset -= monthDays;

            // 处理闰月
            if (leapMonth > 0 && iMonth == leapMonth) {
                int leapDays = leapDays(this.year);
                if (offset < leapDays) {
                    this.month = iMonth;
                    this.leap = true;
                    this.day = (int) offset + 1;
                    break;
                }
                offset -= leapDays;
            }
        }
    }

    public static int lYearDays(int y) {
        int i, sum = 348;
        for (i = 0x8000; i > 0x8; i >>= 1) sum += (lunarInfo[y - 1900] & i) != 0 ? 1 : 0;
        return (sum + leapDays(y));
    }

    public static int leapMonth(int y) {
        return (lunarInfo[y - 1900] & 0xf);
    }

    public static int leapDays(int y) {
        if (leapMonth(y) != 0) {
            if ((lunarInfo[y - 1900] & 0x10000) != 0) return 30;
            else return 29;
        } else return 0;
    }

    public static int monthDays(int y, int m) {
        return ((lunarInfo[y - 1900] & (0x10000 >> m)) == 0 ? 29 : 30);
    }

    public String getLunarYear() {
        return Gan[(year - 4) % 10] + Zhi[(year - 4) % 12] + "年";
    }

    public String getLunarMonth() {
        String monthStr = leap ? "闰" : "";
        monthStr += nStr1[month];
        return monthStr + "月";
    }

    public String getLunarDay() {
        return nStr2[day - 1];
    }

    public String getFullLunarText() {
        return getLunarYear() + " " + getLunarMonth() + getLunarDay();
    }

    public String getAnimalYear() {
        return Animals[(year - 4) % 12];
    }
}