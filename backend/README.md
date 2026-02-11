# ShadowAI Backend with Genkit

This backend provides AI orchestration services using Google's Genkit framework, integrating with Gemini AI models for task planning and image generation coordination.

## Prerequisites

- **Node.js >= 18.0.0** (Current version detected: 16.20.0 - Please upgrade to Node.js 18+)
- npm or yarn package manager

## Installation

1. Install dependencies:
   ```bash
   npm install
   ```

2. Configure environment variables:
   - Copy `.env` file and update `GOOGLE_GENAI_API_KEY` with your Google AI API key
   - Get your API key from [Google AI Studio](https://makersuite.google.com/app/apikey)

## Available AI Flows

### planTask
Creates detailed plans for user-described tasks using Gemini Pro.

**Input:** String description of the task
**Output:** Detailed task plan

### generateImage
Coordinates image generation parameters based on prompts and styles.

**Input:** Object with `prompt` and `style` properties
**Output:** Detailed image generation parameters

## Usage

### Development
```bash
npm run dev
```

### Production
```bash
npm start
```

### Testing Flows
```javascript
const { planTask, generateImage } = require('./index');

// Plan a task
const plan = await planTask('Build a mobile app for task management');
console.log(plan);

// Generate image parameters
const imageParams = await generateImage({
  prompt: 'A futuristic city skyline',
  style: 'cyberpunk'
});
console.log(imageParams);
```

## Integration with Android App

This Genkit backend can be integrated with your Android application to provide:
- AI-powered task planning (enhancing `PlanParser.kt`)
- Intelligent image generation coordination (working with `NovitaService.kt` and `PixAiService.kt`)
- Advanced AI orchestration for complex workflows

## Firebase Integration

The backend works alongside your Firebase setup for:
- User authentication (Google Sign-In)
- Data persistence
- Real-time monitoring via Firebase Gen AI Monitoring

## Node.js Version Issue

⚠️ **Important:** The current Node.js version (16.20.0) is below the minimum requirement (18.0.0) for Genkit. Please upgrade to Node.js 18+ to ensure full compatibility.

To upgrade Node.js:
1. Download and install Node.js 18+ from [nodejs.org](https://nodejs.org/)
2. Verify installation: `node --version`

## API Key Setup

1. Visit [Google AI Studio](https://makersuite.google.com/app/apikey)
2. Create a new API key
3. Add it to your `.env` file:
   ```
   GOOGLE_GENAI_API_KEY=your_actual_api_key_here
   ```

## Monitoring

Use Firebase Gen AI Monitoring to track:
- API usage and costs
- Model performance
- Error rates
- Token consumption

Access at: https://console.firebase.google.com/project/shadowai-4663b/genai_monitoring