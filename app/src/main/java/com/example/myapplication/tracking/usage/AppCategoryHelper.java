package com.example.myapplication.tracking.usage;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import com.example.myapplication.R;

import java.util.Locale;

public class AppCategoryHelper {

    public static final int[] CATEGORY_COLOR_RES_IDS = new int[]{
            R.color.purple,
            R.color.blue,
            R.color.green,
            R.color.orange,
            R.color.mint,
            R.color.pink,
            R.color.yellow,
            R.color.red
    };

    public static String getCategoryName(PackageManager pm, String pkg) {
        if (pkg == null || pkg.isEmpty()) return "Other";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && pm != null) {
            try {
                ApplicationInfo appInfo = pm.getApplicationInfo(pkg, 0);
                int appCategory = appInfo.category;
                switch (appCategory) {
                    case ApplicationInfo.CATEGORY_GAME: return "Games";
                    case ApplicationInfo.CATEGORY_AUDIO: return "Audio & Music";
                    case ApplicationInfo.CATEGORY_VIDEO: return "Video & Movies";
                    case ApplicationInfo.CATEGORY_IMAGE: return "Photos & Images";
                    case ApplicationInfo.CATEGORY_SOCIAL: return "Social";
                    case ApplicationInfo.CATEGORY_NEWS: return "News & Reading";
                    case ApplicationInfo.CATEGORY_MAPS: return "Maps & Navigation";
                    case ApplicationInfo.CATEGORY_PRODUCTIVITY: return "Productivity";
                    case ApplicationInfo.CATEGORY_ACCESSIBILITY: return "Accessibility";
                    default: break;
                }
            } catch (PackageManager.NameNotFoundException ignored) {}
        }

        String lowerPkg = pkg.toLowerCase(Locale.ROOT);

        if (lowerPkg.contains("instagram") || lowerPkg.contains("facebook") || lowerPkg.contains("whatsapp") ||
                lowerPkg.contains("twitter") || lowerPkg.contains("tiktok") || lowerPkg.contains("telegram") ||
                lowerPkg.contains("snapchat") || lowerPkg.contains("reddit") || lowerPkg.contains("linkedin") ||
                lowerPkg.contains("pinterest") || lowerPkg.contains("messenger") || lowerPkg.contains("discord") ||
                lowerPkg.contains("x.android")) {
            return "Social";
        }

        if (lowerPkg.contains("youtube") || lowerPkg.contains("netflix") || lowerPkg.contains("spotify") ||
                lowerPkg.contains("twitch") || lowerPkg.contains("hulu") || lowerPkg.contains("disney") ||
                lowerPkg.contains("gallery") || lowerPkg.contains("photos") || lowerPkg.contains("vlc") ||
                lowerPkg.contains("music") || lowerPkg.contains("video") || lowerPkg.contains("primevideo")) {
            return "Entertainment";
        }

        if (lowerPkg.contains("gmail") || lowerPkg.contains("outlook") || lowerPkg.contains("slack") ||
                lowerPkg.contains("teams") || lowerPkg.contains("docs") || lowerPkg.contains("sheets") ||
                lowerPkg.contains("calendar") || lowerPkg.contains("notes") || lowerPkg.contains("keep") ||
                lowerPkg.contains("calculator") || lowerPkg.contains("clock") || lowerPkg.contains("android.studio") ||
                lowerPkg.contains("drive") || lowerPkg.contains("zoom") || lowerPkg.contains("notion")) {
            return "Productivity";
        }

        if (lowerPkg.contains("chrome") || lowerPkg.contains("browser") || lowerPkg.contains("firefox") ||
                lowerPkg.contains("edge") || lowerPkg.contains("opera") || lowerPkg.contains("safari")) {
            return "Browser";
        }

        if (lowerPkg.contains("amazon") || lowerPkg.contains("ebay") || lowerPkg.contains("walmart") ||
                lowerPkg.contains("target") || lowerPkg.contains("shopping") || lowerPkg.contains("aliexpress") ||
                lowerPkg.contains("etsy")) {
            return "Shopping";
        }

        if (lowerPkg.contains("launcher") || lowerPkg.contains("nexuslauncher") || lowerPkg.contains("trebuchet") ||
                lowerPkg.contains("systemui") || lowerPkg.contains("settings") || lowerPkg.contains("android")) {
            return "System";
        }

        return "Other";
    }

    public static int getCategoryIconRes(String categoryName) {
        if (categoryName == null) return R.drawable.ic_cat_other;
        return switch (categoryName) {
            case "Social" -> R.drawable.ic_cat_social;
            case "Entertainment", "Video & Movies" -> R.drawable.ic_cat_entertainment;
            case "Games" -> R.drawable.ic_cat_games;
            case "Productivity" -> R.drawable.ic_cat_productivity;
            case "Browser" -> R.drawable.ic_cat_browser;
            case "Shopping" -> R.drawable.ic_cat_shopping;
            case "News & Reading" -> R.drawable.ic_cat_news;
            case "Audio & Music" -> R.drawable.ic_cat_audio;
            case "Photos & Images" -> R.drawable.ic_cat_photos;
            case "Maps & Navigation" -> R.drawable.ic_cat_maps;
            case "System", "Accessibility" -> R.drawable.ic_cat_system;
            default -> R.drawable.ic_cat_other;
        };
    }

    public static boolean isDoomscrollApp(PackageManager pm, String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;

        String category = getCategoryName(pm, pkg);
        if ("Social".equalsIgnoreCase(category) || "News & Reading".equalsIgnoreCase(category)) {
            return true;
        }

        String lowerPkg = pkg.toLowerCase(Locale.ROOT);
        return lowerPkg.contains("instagram") || lowerPkg.contains("tiktok") || lowerPkg.contains("reddit") ||
                lowerPkg.contains("twitter") || lowerPkg.contains("x.android") || lowerPkg.contains("facebook") ||
                lowerPkg.contains("snapchat") || lowerPkg.contains("threads") || lowerPkg.contains("tumblr") ||
                lowerPkg.contains("pinterest") ||
                lowerPkg.contains("buzzfeed") || lowerPkg.contains("youtube") || lowerPkg.contains("twitch") ||
                lowerPkg.contains("news") || lowerPkg.contains("shorts") || lowerPkg.contains("reels") ||
                lowerPkg.contains("feedly") || lowerPkg.contains("dailymail") || lowerPkg.contains("boredpanda");
    }
}
