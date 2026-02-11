package com.shadowai.uicomposition

import com.shadowai.uiparams.ModelParameter
import com.shadowai.uiparams.ParameterSchema

/**
 * Default parameter schemas for transformation views.
 */
object ViewParameterSchemas {
    val chatSchema: ParameterSchema = ParameterSchema(
        id = "chat",
        name = "Chat Parameters",
        parameters = listOf(
            ModelParameter.NumberParameter(
                id = "temperature",
                name = "Temperature",
                description = "Controls randomness in the response.",
                defaultValue = 0.7,
                min = 0.0,
                max = 2.0,
                step = 0.1
            ),
            ModelParameter.NumberParameter(
                id = "maxTokens",
                name = "Max Tokens",
                description = "Upper bound on generated tokens.",
                defaultValue = 512,
                min = 1,
                max = 4096,
                step = 1
            ),
            ModelParameter.NumberParameter(
                id = "topP",
                name = "Top P",
                description = "Nucleus sampling threshold.",
                defaultValue = 0.9,
                min = 0.0,
                max = 1.0,
                step = 0.05
            )
        )
    )

    val imageSchema: ParameterSchema = ParameterSchema(
        id = "image",
        name = "Image Parameters",
        parameters = listOf(
            ModelParameter.NumberParameter(
                id = "width",
                name = "Width",
                description = "Output image width in pixels.",
                defaultValue = 1024,
                min = 256,
                max = 2048,
                step = 64
            ),
            ModelParameter.NumberParameter(
                id = "height",
                name = "Height",
                description = "Output image height in pixels.",
                defaultValue = 1024,
                min = 256,
                max = 2048,
                step = 64
            ),
            ModelParameter.NumberParameter(
                id = "steps",
                name = "Steps",
                description = "Diffusion steps for sampling.",
                defaultValue = 24,
                min = 1,
                max = 150,
                step = 1
            ),
            ModelParameter.NumberParameter(
                id = "guidanceScale",
                name = "Guidance Scale",
                description = "Prompt adherence strength.",
                defaultValue = 7.5,
                min = 1.0,
                max = 20.0,
                step = 0.5
            ),
            ModelParameter.NumberParameter(
                id = "seed",
                name = "Seed",
                description = "Random seed for reproducibility.",
                defaultValue = 0,
                min = 0,
                max = 999999,
                step = 1
            ),
            ModelParameter.StringParameter(
                id = "negativePrompt",
                name = "Negative Prompt",
                description = "Elements to avoid in the output.",
                defaultValue = ""
            )
        )
    )

    val videoSchema: ParameterSchema = ParameterSchema(
        id = "video",
        name = "Video Parameters",
        parameters = listOf(
            ModelParameter.NumberParameter(
                id = "durationSeconds",
                name = "Duration (s)",
                description = "Length of the generated video.",
                defaultValue = 4,
                min = 1,
                max = 20,
                step = 1
            ),
            ModelParameter.NumberParameter(
                id = "fps",
                name = "FPS",
                description = "Frames per second.",
                defaultValue = 24,
                min = 8,
                max = 60,
                step = 1
            ),
            ModelParameter.EnumParameter(
                id = "resolution",
                name = "Resolution",
                description = "Output resolution preset.",
                defaultValue = "720p",
                options = listOf("480p", "720p", "1080p")
            ),
            ModelParameter.NumberParameter(
                id = "motionStrength",
                name = "Motion Strength",
                description = "Controls motion intensity.",
                defaultValue = 0.6,
                min = 0.0,
                max = 1.0,
                step = 0.05
            )
        )
    )

    val audioSchema: ParameterSchema = ParameterSchema(
        id = "audio",
        name = "Audio Parameters",
        parameters = listOf(
            ModelParameter.EnumParameter(
                id = "voice",
                name = "Voice",
                description = "Voice preset for synthesis.",
                defaultValue = "neutral",
                options = listOf("neutral", "warm", "bright", "deep")
            ),
            ModelParameter.NumberParameter(
                id = "speed",
                name = "Speed",
                description = "Playback speed.",
                defaultValue = 1.0,
                min = 0.5,
                max = 2.0,
                step = 0.1
            ),
            ModelParameter.NumberParameter(
                id = "pitch",
                name = "Pitch",
                description = "Pitch shift in semitones.",
                defaultValue = 0,
                min = -12,
                max = 12,
                step = 1
            ),
            ModelParameter.EnumParameter(
                id = "format",
                name = "Format",
                description = "Audio format.",
                defaultValue = "wav",
                options = listOf("wav", "mp3", "flac")
            )
        )
    )

    val editSchema: ParameterSchema = ParameterSchema(
        id = "edit",
        name = "Edit Parameters",
        parameters = listOf(
            ModelParameter.NumberParameter(
                id = "strength",
                name = "Edit Strength",
                description = "Intensity of the edit operation.",
                defaultValue = 0.6,
                min = 0.0,
                max = 1.0,
                step = 0.05
            ),
            ModelParameter.StringParameter(
                id = "instruction",
                name = "Edit Instruction",
                description = "Describe the edit to apply.",
                defaultValue = ""
            )
        )
    )
}
