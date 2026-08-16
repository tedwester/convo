package tedwester.convo.ui.chat.composer

import kotlin.math.abs
import kotlin.math.exp

internal const val CLOUD_NEUTRAL_DIAMETER = 0.55f

internal const val CLOUD_LISTEN_SCALE = 1.12f
internal const val CLOUD_TRANSCRIBE_SCALE = 1.0f
internal const val CLOUD_WAIT_SCALE = 1.0f
internal const val CLOUD_LISTEN_VOLUME_PULSE = 0.14f
internal const val CLOUD_SCALE_RATE = 11f

internal const val CLOUD_LISTEN_SPEED = 0.72f
internal const val CLOUD_LISTEN_SPEED_GAIN = 0.78f
internal const val CLOUD_LISTEN_ACTIVITY = 0.28f
internal const val CLOUD_LISTEN_ACTIVITY_GAIN = 0.32f

internal const val CLOUD_TRANSCRIBE_SPEED = 1.85f
internal const val CLOUD_TRANSCRIBE_ACTIVITY = 0.92f

internal const val CLOUD_WAIT_SPEED = 0.42f
internal const val CLOUD_WAIT_ACTIVITY = 0.22f

internal fun damp(current: Float, target: Float, rate: Float, deltaSeconds: Float): Float =
    current + (target - current) * (1f - exp(-rate * deltaSeconds))

internal const val CLOUD_SHADER = """
uniform float2 u_resolution;
uniform float  u_time;
uniform float  u_activity;

float hash(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hash(i), hash(i + float2(1.0, 0.0)), u.x),
        mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x),
        u.y
    );
}

float fbm(float2 p) {
    float value = 0.0;
    float amplitude = 0.52;
    float2x2 rotation = float2x2(0.80, 0.60, -0.60, 0.80);
    for (int octave = 0; octave < 5; octave++) {
        value += amplitude * noise(p);
        p = rotation * p * 1.92 + float2(9.7, 4.3);
        amplitude *= 0.5;
    }
    return value;
}

half4 main(float2 fragCoord) {
    float2 uv = float2(fragCoord.x, u_resolution.y - fragCoord.y) / u_resolution;
    float2 centered = uv - 0.5;
    float radius = length(centered);
    float edge = 1.0 - smoothstep(0.488, 0.5, radius);
    if (edge <= 0.0) return half4(0.0);

    float2 p = centered * 2.0;
    float t = u_time;

    float2 warp = float2(
        fbm(p * 1.02 + float2(t * 0.34, -t * 0.24)),
        fbm(p * 1.08 + float2(-t * 0.27, t * 0.32) + float2(6.7, 2.9))
    );
    float2 curl = float2(
        sin(p.y * 2.4 + t * 0.68 + warp.y * 3.2),
        cos(p.x * 2.1 - t * 0.61 + warp.x * 3.0)
    );
    float2 warped = p + (warp - 0.5) * (1.18 + u_activity * 0.38) + curl * (0.035 + u_activity * 0.07);
    float broad = fbm(warped * 0.92 + float2(t * 0.14, -t * 0.18));
    float folded = fbm(warped * 1.66 + float2(-t * 0.23, t * 0.19) + 5.2);
    float field = mix(broad, folded, 0.3 + u_activity * 0.14);

    float horizon = 0.46 + 0.08 * sin((uv.x + warp.x * 0.2) * 5.4 + t * 0.42) + 0.16 * (broad - 0.5);
    float upper = smoothstep(horizon - 0.12, horizon + 0.08, uv.y);
    float band = exp(-pow((uv.y - horizon) * (5.2 + u_activity * 0.8), 2.0));
    float cloud = smoothstep(0.24, 0.79, field);

    float3 deepPeriwinkle = float3(0.36, 0.39, 0.985);
    float3 upperPeriwinkle = float3(0.48, 0.56, 0.985);
    float3 lowerLavender = float3(0.72, 0.78, 0.975);
    float3 milk = float3(0.89, 0.92, 0.995);

    float3 color = mix(lowerLavender, upperPeriwinkle, upper);
    float upperDepth = upper * (0.14 + smoothstep(0.42, 0.78, folded) * 0.5);
    color = mix(color, deepPeriwinkle, upperDepth);

    float milkAmount = clamp(band * (0.42 + cloud * 0.62), 0.0, 0.88);
    color = mix(color, milk, milkAmount);

    float lowerMist = (1.0 - upper) * smoothstep(0.58, 0.9, broad) * 0.18;
    color = mix(color, milk, lowerMist);

    float grain = (noise(fragCoord * 0.64) - 0.5) / 255.0;
    color += grain;

    return half4(float4(color, edge));
}
"""
