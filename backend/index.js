require('dotenv').config();

const { genkit } = require('genkit');
const { googleAI } = require('@genkit-ai/googleai');

// Configure Genkit with Google AI
const ai = genkit({
  plugins: [
    googleAI({ 
      apiKey: process.env.GOOGLE_GENAI_API_KEY,
      safetySettings: [
        { category: 'HARM_CATEGORY_HARASSMENT', threshold: 'BLOCK_NONE' },
        { category: 'HARM_CATEGORY_HATE_SPEECH', threshold: 'BLOCK_NONE' },
        { category: 'HARM_CATEGORY_SEXUALLY_EXPLICIT', threshold: 'BLOCK_NONE' },
        { category: 'HARM_CATEGORY_DANGEROUS_CONTENT', threshold: 'BLOCK_NONE' },
        { category: 'HARM_CATEGORY_CIVIC_INTEGRITY', threshold: 'BLOCK_NONE' },
      ]
    })
  ],
  model: googleAI.GeminiPro, // Use Gemini Pro model
});

// Define a simple AI flow for task planning
const planTask = ai.defineFlow(
  { name: 'planTask', inputSchema: { type: 'string' }, outputSchema: { type: 'string' } },
  async (taskDescription) => {
    const llm = ai.generate({
      model: googleAI.GeminiPro,
      prompt: `Create a detailed plan for the following task: ${taskDescription}`,
      config: {
        temperature: 0.7,
      },
    });

    return llm.response.text();
  }
);

// Define a flow for AI image generation coordination
const generateImage = ai.defineFlow(
  { name: 'generateImage', inputSchema: { type: 'object', properties: { prompt: { type: 'string' }, style: { type: 'string' } } }, outputSchema: { type: 'string' } },
  async ({ prompt, style }) => {
    const llm = ai.generate({
      model: googleAI.GeminiPro,
      prompt: `Generate an image with the following description: ${prompt}. Style: ${style}. Provide detailed parameters for image generation.`,
      config: {
        temperature: 0.8,
      },
    });

    return llm.response.text();
  }
);

// Export flows for use in other parts of the application
module.exports = {
  planTask,
  generateImage,
  ai,
};