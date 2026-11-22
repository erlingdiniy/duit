package com.diniy.duit.core.ui.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import org.intellij.lang.annotations.Language

/**
 * Native Android AGSL (API 33+) port of `zakisheriff/Liquid-Glass` optics (`refraction.ts`).
 *
 * Single GPU fragment pass computes:
 * 1. Exact rounded-rect SDF (`d` = inward distance from border).
 * 2. Convex squircle bevel height `h(t) = (1 - (1 - t)^4)^(1/4)` + Snell's law displacement.
 * 3. Surface mode (inward edge bend) vs Lens mode (outward droplet magnification + RGB dispersion).
 * 4. Saturation boost + Candy Bubble tint veil so playful hues stay crisp and AA-readable.
 * 5. Diagonal upper-left specular rim + opposite corner internal reflection + touch-point bloom.
 */
internal object LiquidGlassShaderSource {

    @Language("AGSL")
    const val AGSL = """
        uniform shader backdrop;
        uniform float2 uSize;
        uniform float uRadius;
        uniform float uBezel;
        uniform float uThickness;
        uniform float uIor;
        uniform float uZoom;
        uniform float uDispersion;
        uniform float uSaturation;
        uniform float uIsLens;
        uniform float uIsDark;
        uniform float uRefractFactor;
        uniform float4 uTint;
        uniform float4 uRim;
        uniform float2 uTouchPos;
        uniform float uTouchAlpha;

        float3 adjustSaturation(float3 rgb, float sat) {
            float luma = dot(rgb, float3(0.2126, 0.7152, 0.0722));
            return clamp(mix(float3(luma), rgb, sat), 0.0, 1.0);
        }

        float bevelShift(float dist, float bezel, float thickness, float ior) {
            if (dist < 0.0 || dist >= bezel || bezel <= 0.5) return 0.0;
            float t = clamp(dist / bezel, 0.004, 0.999);
            float q = 1.0 - t;
            float q2 = q * q;
            float base = max(0.0005, 1.0 - q2 * q2);
            float height = pow(base, 0.25);
            float slope = (q2 * q) / pow(base, 0.75);
            float incident = atan(slope);
            float sinR = clamp(sin(incident) / max(1.01, ior), -0.999, 0.999);
            float refracted = asin(sinR);
            return tan(incident - refracted) * thickness * (0.35 + 0.65 * height);
        }

        half4 main(float2 fragCoord) {
            float2 halfSize = max(uSize * 0.5, float2(1.0));
            float r = clamp(uRadius, 1.0, min(halfSize.x, halfSize.y));
            float2 p = fragCoord - halfSize;

            // Signed distance from rounded-rect edge (positive inside)
            float2 q = abs(p) - (halfSize - float2(r));
            float2 o = max(q, float2(0.0));
            float len = length(o);
            float d = r - (len + min(max(q.x, q.y), 0.0));

            if (d < -1.5) {
                return half4(0.0);
            }

            // Outward unit normal at the bevel
            float2 n = float2(0.0);
            if (len > 0.0001) {
                n = (o / len) * sign(p);
            } else if (q.x > q.y) {
                n = float2(sign(p.x), 0.0);
            } else {
                n = float2(0.0, sign(p.y));
            }

            // Smooth Snell bevel refraction (inward on surfaces, outward on lenses)
            float dir = uIsLens > 0.5 ? 1.0 : -1.0;
            float shift = bevelShift(d, uBezel, uThickness, uIor) * dir * uRefractFactor;
            float2 baseOffset = n * shift;

            // Subtle interior magnification (Lens mode only)
            float mag = uIsLens > 0.5 ? (1.0 - 1.0 / max(0.75, uZoom)) * uRefractFactor : 0.0;
            float2 zoomOffset = -p * mag;

            float2 sampleBase = clamp(fragCoord + baseOffset + zoomOffset, float2(0.5), uSize - float2(0.5));
            float3 refractedRgb;
            if (uDispersion > 0.01 && uIsLens > 0.5 && uRefractFactor > 0.05) {
                float spread = 0.18 * uDispersion;
                float2 pR = clamp(fragCoord + baseOffset * (1.0 + spread) + zoomOffset, float2(0.5), uSize - float2(0.5));
                float2 pB = clamp(fragCoord + baseOffset * (1.0 - spread) + zoomOffset, float2(0.5), uSize - float2(0.5));
                float rCh = float(backdrop.eval(pR).r);
                float gCh = float(backdrop.eval(sampleBase).g);
                float bCh = float(backdrop.eval(pB).b);
                refractedRgb = float3(rCh, gCh, bCh);
            } else {
                refractedRgb = float3(backdrop.eval(sampleBase).rgb);
            }

            // Optical brightness: lift softly in Light only so Dark stays pure deep obsidian
            float brightLift = uIsDark > 0.5 ? 1.0 : 1.04;
            float3 color = clamp(adjustSaturation(refractedRgb, uSaturation) * brightLift, 0.0, 1.0);

            // Clean glass tint veil
            color = mix(color, uTint.rgb, uTint.a);

            // Interactive touch-point radial bloom
            if (uTouchAlpha > 0.005) {
                float touchDist = length(fragCoord - uTouchPos);
                float bloomRadius = max(uSize.x, uSize.y) * 0.42;
                float bloom = exp(-(touchDist * touchDist) / max(1.0, bloomRadius * bloomRadius * 0.35));
                color = clamp(color + uRim.rgb * (bloom * 0.18 * uTouchAlpha), 0.0, 1.0);
            }

            // Silky diagonal upper-left specular rim + subtle Fresnel edge (no harsh wireframe outline)
            float facing = -(n.x + n.y) * 0.70710678;
            float light = (uIsDark > 0.5 ? 0.14 : 0.20) +
                (uIsDark > 0.5 ? 0.86 : 0.80) * pow(abs(facing), 2.4) * (facing > 0.0 ? 1.0 : 0.50);
            float hair = 1.4;
            float line = exp(-pow((d - hair) / hair, 2.0));
            float innerFresnel = exp(-pow(d / max(2.0, uBezel * 0.25), 2.0)) * (uIsDark > 0.5 ? 0.04 : 0.07);
            float rimStrength = clamp((line * (uIsLens > 0.5 ? 0.72 : 0.58) + innerFresnel) * light, 0.0, 1.0) * uRim.a;
            color = clamp(color + uRim.rgb * rimStrength, 0.0, 1.0);

            float aa = clamp(d + 0.75, 0.0, 1.0);
            return half4(half3(color * aa), half(aa));
        }
    """
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class LiquidGlassShaderHandle {
    private val shader = RuntimeShader(LiquidGlassShaderSource.AGSL)

    fun buildRenderEffect(
        widthPx: Float,
        heightPx: Float,
        radiusPx: Float,
        bezelPx: Float,
        thicknessPx: Float,
        blurPx: Float,
        spec: LiquidGlassSurfaceSpec,
        refractFactor: Float,
        touchPos: Offset,
        touchAlpha: Float
    ): RenderEffect {
        shader.setFloatUniform("uSize", widthPx.coerceAtLeast(1f), heightPx.coerceAtLeast(1f))
        shader.setFloatUniform("uRadius", radiusPx)
        shader.setFloatUniform("uBezel", bezelPx)
        shader.setFloatUniform("uThickness", thicknessPx)
        shader.setFloatUniform("uIor", spec.ior)
        shader.setFloatUniform("uZoom", spec.zoom)
        shader.setFloatUniform("uDispersion", spec.dispersion)
        shader.setFloatUniform("uSaturation", spec.saturation)
        shader.setFloatUniform("uIsLens", if (spec.kind == LiquidGlassKind.Lens) 1f else 0f)
        shader.setFloatUniform("uIsDark", if (spec.isDark) 1f else 0f)
        shader.setFloatUniform("uRefractFactor", refractFactor.coerceIn(0f, 1f))
        shader.setFloatUniform(
            "uTint",
            spec.tintColor.red,
            spec.tintColor.green,
            spec.tintColor.blue,
            spec.tintAlpha
        )
        shader.setFloatUniform(
            "uRim",
            spec.rimColor.red,
            spec.rimColor.green,
            spec.rimColor.blue,
            spec.rimAlpha
        )
        shader.setFloatUniform("uTouchPos", touchPos.x, touchPos.y)
        shader.setFloatUniform("uTouchAlpha", touchAlpha.coerceIn(0f, 1f))

        val refractEffect = RenderEffect.createRuntimeShaderEffect(shader, "backdrop")
        return if (blurPx >= 1f) {
            val blurEffect = RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
            RenderEffect.createChainEffect(refractEffect, blurEffect)
        } else {
            refractEffect
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
internal fun createBackdropBlurEffect(blurPx: Float): RenderEffect? {
    if (blurPx < 1f) return null
    return RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
}
