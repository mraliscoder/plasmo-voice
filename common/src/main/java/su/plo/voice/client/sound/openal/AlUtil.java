package su.plo.voice.client.sound.openal;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;

public class AlUtil {
    private static final Logger LOGGER = LogManager.getLogger();

    private static String getErrorMessage(int errorCode) {
        return switch (errorCode) {
            case 40961 -> "Invalid name parameter.";
            case 40962 -> "Invalid enumerated parameter value.";
            case 40963 -> "Invalid parameter parameter value.";
            case 40964 -> "Invalid operation.";
            case 40965 -> "Unable to allocate memory.";
            default -> "An unrecognized error occurred.";
        };
    }

    public static boolean checkErrors(String sectionName) {
        int i = AL10.alGetError();
        if (i != 0) {
            LOGGER.error("{}: {}", sectionName, getErrorMessage(i));
            return true;
        } else {
            return false;
        }
    }

    private static String getAlcErrorMessage(int errorCode) {
        return switch (errorCode) {
            case 40961 -> "Invalid device.";
            case 40962 -> "Invalid context.";
            case 40963 -> "Illegal enum.";
            case 40964 -> "Invalid value.";
            case 40965 -> "Unable to allocate memory.";
            default -> "An unrecognized error occurred.";
        };
    }

    public static boolean checkAlcErrors(long deviceHandle, String sectionName) {
        int i = ALC10.alcGetError(deviceHandle);
        if (i != 0) {
            LOGGER.error("{}{}: {}", sectionName, deviceHandle, getAlcErrorMessage(i));
            return true;
        } else {
            return false;
        }
    }

    public static int getFormatId(AudioFormat format) {
        Encoding encoding = format.getEncoding();
        int i = format.getChannels();
        int j = format.getSampleSizeInBits();
        if (encoding.equals(Encoding.PCM_UNSIGNED) || encoding.equals(Encoding.PCM_SIGNED)) {
            if (i == 1) {
                if (j == 8) {
                    return 4352;
                }

                if (j == 16) {
                    return 4353;
                }
            } else if (i == 2) {
                if (j == 8) {
                    return 4354;
                }

                if (j == 16) {
                    return 4355;
                }
            }
        }

        throw new IllegalArgumentException("Invalid audio format: " + format);
    }
}
