package com.example.converttable;

/** Deterministic visual animation; no server ticker, packets or persisted counter. */
public record AnimationTrack(boolean rotates, float duration, float[][] keys) {
    public AnimationTrack {
        if (!Float.isFinite(duration) || duration <= 0 || keys.length < 2
                || keys[0][0] != 0 || keys[keys.length - 1][0] != duration) {
            throw new IllegalArgumentException("Animation must span its full positive duration");
        }
        float[][] copy = new float[keys.length][2];
        for (int i = 0; i < keys.length; i++) {
            if (keys[i].length != 2 || !Float.isFinite(keys[i][0]) || !Float.isFinite(keys[i][1])
                    || (i > 0 && keys[i][0] <= keys[i - 1][0])) {
                throw new IllegalArgumentException("Animation keys must be finite and ordered");
            }
            copy[i] = keys[i].clone();
        }
        keys = copy;
    }

    public float sample(double time) {
        double phase = ((time % duration) + duration) % duration;
        for (int i = 1; i < keys.length; i++) {
            if (phase <= keys[i][0]) {
                double t = (phase - keys[i - 1][0]) / (keys[i][0] - keys[i - 1][0]);
                if (!rotates) t = t * t * (3 - 2 * t);
                return (float) (keys[i - 1][1] + (keys[i][1] - keys[i - 1][1]) * t);
            }
        }
        return keys[0][1];
    }
}
