package codex.utils;

import java.text.DecimalFormat;

public class FileUtils {

    public enum Dimension {
        KB, MB, GB, TB, AUTO
    }

    public static String formatFileSize(long size) {
        return formatFileSize(size, Dimension.AUTO);
    }

    public static String formatFileSize(long size, Dimension dim) {
        String hrSize;

        double bytes     = size;
        double kilobytes = size/1024.0;
        double megabytes = ((size/1024.0)/1024.0);
        double gigabytes = (((size/1024.0)/1024.0)/1024.0);
        double terabytes = ((((size/1024.0)/1024.0)/1024.0)/1024.0);

        DecimalFormat dec = new DecimalFormat("0.00");

        if ((terabytes > 1 && dim == Dimension.AUTO) || dim == Dimension.TB) {
            hrSize = dec.format(terabytes).concat(" TB");
        } else if ((gigabytes > 1 && dim == Dimension.AUTO) || dim == Dimension.GB) {
            hrSize = dec.format(gigabytes).concat(" GB");
        } else if ((megabytes > 1 && dim == Dimension.AUTO) || dim == Dimension.MB) {
            hrSize = dec.format(megabytes).concat(" MB");
        } else if ((kilobytes > 1 && dim == Dimension.AUTO) || dim == Dimension.KB) {
            hrSize = dec.format(kilobytes).concat(" KB");
        } else {
            hrSize = dec.format(bytes).concat(" B");
        }
        return hrSize;
    }
}
