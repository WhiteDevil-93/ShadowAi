# Firebase + Google AI Pro Integration Architecture

## Overview

This document outlines how to leverage Firebase with your Google AI Pro subscription to enhance the DarcyAI Android project with cloud-first features while maintaining local-first capabilities.

## Current Architecture Assessment

### Existing Components
- **Local-first AI**: Termux/Ollama + llama.cpp (Liquid AI) native inference (CORE - NO REMOVAL)
- **Cloud providers**: Google Gemini (AI Pro), OpenAI, Anthropic, Siray, Novita AI, PixAI, etc.
- **Backend**: Ktor/Kotlin backend for webhook processing and AI orchestration
- **Auth**: Firebase Authentication (Google Sign-In)
- **Storage**: SQLCipher encrypted local database + DataStore preferences

### Integration Opportunities

## 1. Firebase Cloud Firestore

### Use Cases
- **Conversation History**: Store chat sessions across devices
- **User Preferences**: Sync settings between installations
- **Model Catalog**: Cloud-managed AI model configurations (including **Ollama Cloud** & Gemini)
- **Agent Templates**: Shareable automation workflows

### Data Model

... (no changes to data model) ...

## Hybrid Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    DarcyAI App                          │
├─────────────────────────────────────────────────────────┤
│  Local Inference    │    Cloud Inference                │
│  ───────────────    │    ───────────────                │
│  • llama.cpp        │    • Gemini API (AI Pro)          │
│  • Ollama (Local)   │    • Ollama Cloud (NEW)           │
│  • Termux           │    • Novita AI                    │
│                     │    • OpenAI/Anthropic             │
└──────────┬──────────┴─────────────────┬─────────────────┘
```

```
users/{userId}
  ├── profile/
  │   ├── displayName: string
  │   ├── email: string
  │   └── preferences: map
  ├── conversations/{conversationId}
  │   ├── title: string
  │   ├── createdAt: timestamp
  │   ├── messages: array
  │   └── modelUsed: string
  ├── agents/{agentId}
  │   ├── name: string
  │   ├── template: map
  │   └── isPublic: boolean
  └── cache/
      └── modelMetadata: map
```

### Implementation Priority: **HIGH**

## 2. Firebase Cloud Functions

### Use Cases with Google AI Pro

#### A. Gemini API Proxy
```kotlin
// Cloud Function: gemini-proxy
// Trigger: HTTPS callable
// Purpose: Secure API key handling, rate limiting

exports.geminiProxy = functions.https.onCall(async (data, context) => {
  // Validate user subscription
  // Forward request to Gemini API
  // Track usage for quota management
  // Return response with caching
});
```

#### B. Conversation Processing
- Long-running AI tasks without timeout
- Multi-turn conversation summarization
- Agent workflow orchestration

#### C. Webhook Handlers
- Secure webhook verification for AI providers
- Response processing pipeline
- Error handling and retry logic

### Implementation Priority: **HIGH**

## 3. Firebase Authentication (Enhanced)

### Current State
- Google Sign-In implemented ✅


### Enhancements Needed
- **Anonymous Auth**: For temporary sessions
- **Token Refresh**: Improved session management
- **Multi-Device Sync**: Unified authentication state

### Implementation Priority: **MEDIUM**

## 4. Firebase Remote Config

### Use Cases
- **Feature Flags**: Enable/disable AI providers
- **Model Configuration**: Dynamic model selection
- **A/B Testing**: Compare provider performance
- **Rate Limits**: Adjust based on subscription tier

### Example Configuration
```json
{
  "providers": {
    "gemini": {
      "enabled": true,
      "model": "gemini-1.5-pro",
      "maxTokens": 1048576,
      "rateLimit": 1000
    }
  },
  "feature_flags": {
    "cloud_sync": true,
    "agent_sharing": false
  }
}
```

### Implementation Priority: **MEDIUM**

## 5. Firebase Analytics

### Events to Track
- **AI Provider Usage**: Which models are used most
- **Response Times**: Latency tracking per provider
- **Error Rates**: Failure analysis by provider
- **Feature Adoption**: Usage of cloud vs local AI

### Implementation Priority: **LOW**

## 6. Firebase Cloud Messaging

### Use Cases
- **Long-running Task Notifications**: AI generation complete
- **Model Updates**: New models available
- **Subscription Reminders**: Renewal notifications

### Implementation Priority: **LOW**

## 7. Firebase App Check

### Purpose
- Verify API requests come from your app
- Prevent abuse of cloud functions
- Protect Gemini API quota

### Implementation Priority: **MEDIUM**

## Google AI Pro Integration

### API Access
With Google AI Pro, you have access to:
- **Gemini 1.5 Pro**: 1M+ token context window
- **Gemini 1.5 Flash**: Fast, efficient inference
- **Higher rate limits**: More requests per minute

### Architecture Integration

```
┌─────────────────────────────────────────────────────────┐
│                    DarcyAI App                          │
├─────────────────────────────────────────────────────────┤
│  Local Inference    │    Cloud Inference                │
│  ───────────────    │    ───────────────                │
│  • llama.cpp        │    • Gemini API (AI Pro)          │
│  • Ollama           │    • Novita AI                    │
│  • Termux           │    • PixAI                        │
└──────────┬──────────┴─────────────────┬─────────────────┘
           │                            │
           ▼                            ▼
┌─────────────────────────────────────────────────────────┐
│              Firebase Layer                              │
├─────────────────────────────────────────────────────────┤
│  • Firestore: Conversation history & sync               │
│  • Cloud Functions: API proxy & processing              │
│  • Remote Config: Dynamic configuration                 │
│  • App Check: Security & abuse prevention               │
└─────────────────────────────────────────────────────────┘
```

### Gemini API Integration

```kotlin
// app/src/main/java/com/shadowai/app/ai/GeminiProvider.kt

class GeminiProvider(
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestore
) : AiProvider {
    
    override suspend fun generate(request: GenerationRequest): Flow<GenerationResponse> {
        // Check local cache first
        // If miss, call Cloud Function (gemini-proxy)
        // Cache response in Firestore
        // Track usage in Firestore
    }
    
    override suspend fun stream(request: GenerationRequest): Flow<String> {
        // Stream response from Gemini
        // Cache tokens progressively
    }
}
```

## Implementation Roadmap

### Phase 1: Core Infrastructure (Week 1)
- [ ] Configure Firebase project with Android app
- [ ] Set up Firestore data model
- [ ] Implement Cloud Functions for Gemini proxy
- [ ] Add App Check for security

### Phase 2: Conversation Sync (Week 2)
- [ ] Implement conversation storage in Firestore
- [ ] Add cross-device sync
- [ ] Implement conflict resolution
- [ ] Add offline support with local cache

### Phase 3: Advanced Features (Week 3)
- [ ] Agent template sharing
- [ ] A/B testing with Remote Config
- [ ] Analytics integration
- [ ] Push notifications for long-running tasks

## Dependencies Required

```kotlin
// app/build.gradle.kts
dependencies {
    // Firebase BoM
    implementation(platform("com.google.firebase:firebase-bom:33.0.0"))
    
    // Authentication
    implementation("com.google.firebase:firebase-auth-ktx")
    
    // Firestore
    implementation("com.google.firebase:firebase-firestore-ktx")
    
    // Cloud Functions
    implementation("com.google.firebase:firebase-functions-ktx")
    
    // Remote Config
    implementation("com.google.firebase:firebase-config-ktx")
    
    // Analytics
    implementation("com.google.firebase:firebase-analytics-ktx")
    
    // Crashlytics
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    
    // Cloud Messaging
    implementation("com.google.firebase:firebase-messaging-ktx")
    
    // App Check
    implementation("com.google.firebase:firebase-appcheck-ktx")
}
```

## Security Considerations

### Firestore Security Rules
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
    match /conversations/{conversationId} {
      allow read, write: if request.auth != null && 
        resource.data.userId == request.auth.uid;
    }
  }
}
```

### Cloud Function Security
- Verify Firebase Auth token
- Check subscription status
- Implement rate limiting
- Log all API calls

## Cost Estimation

### Firebase Free Tier Limits
- **Firestore**: 1GB storage, 50K reads/day
- **Cloud Functions**: 2M invocations/month
- **Auth**: Unlimited
- **Analytics**: Unlimited

### Google AI Pro Benefits
- Higher Firestore limits included
- Priority access to Gemini API
- Increased Cloud Functions quotas

## Success Metrics

1. **Conversation Sync**: % of users with multi-device sessions
2. **API Usage**: Gemini API calls per user
3. **Latency**: Response time comparison (local vs cloud)
4. **Error Rate**: Failures by provider
5. **Feature Adoption**: Cloud sync enablement rate

## Conclusion

Firebase integration with Google AI Pro subscription provides:
- **Scalable cloud infrastructure** for AI services
- **Cross-device synchronization** of user data
- **Secure API handling** through Cloud Functions
- **Dynamic configuration** via Remote Config
- **Usage analytics** for optimization

The hybrid architecture maintains local-first capabilities while adding cloud-powered features for enhanced AI interaction.
