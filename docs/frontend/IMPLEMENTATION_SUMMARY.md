# Meridian Frontend - Critical Pages Implementation

This document summarizes all critical frontend pages that have been implemented for the Meridian platform.

## Overview

The Meridian frontend consists of a modern Next.js application with React components, Apollo Client for GraphQL, and Tailwind CSS for styling. All pages support authentication, responsive design, and real-time updates where applicable.

## Implemented Pages & Components

### 1. Live Job Page

**Location:** `/app/jobs/[id]/live/page.tsx`

**Purpose:** Display real-time research progress with streaming agent updates

**Key Features:**

- Live progress tracking with percentage bar
- Agent status cards (Planner, Research, Analysis, Critic, Synthesizer)
- Color-coded indicators (running, complete, failed)
- SSE event log for debugging
- Error handling and recovery
- Auto-redirect to report on completion
- Job metadata display (provider, depth, status)

**Technologies:**

- Server-Sent Events (SSE) via custom `useSSE` hook
- Apollo GraphQL queries for polling fallback
- Real-time status animations

### 2. Report Page

**Location:** `/app/jobs/[id]/report/page.tsx`

**Purpose:** Display completed research report with formatting and export options

**Key Features:**

- Markdown-rendered content with syntax highlighting
- Executive summary section
- Confidence score display
- Source citations with links
- Export to PDF/Markdown
- Share functionality (URL copy + native share)
- Job metadata (provider, depth, duration)
- Print-friendly styling

**Technologies:**

- MDXContent component for rich markdown rendering
- react-syntax-highlighter for code blocks
- Custom export buttons with dropdown menus

### 3. Settings Page

**Location:** `/app/settings/page.tsx`

**Purpose:** User account management, preferences, and API keys

**Key Features:**

- Account info display (email, name)
- Theme selection (dark/light/system)
- Default LLM provider selection
- Research depth preference
- Email notification toggle
- API key management (create, view, delete)
- Secure key display with copy-to-clipboard
- Session logout

**Technologies:**

- Apollo mutations for updates
- Real-time preference sync
- Secure key handling with auto-hide

### 4. Pricing Page

**Location:** `/app/pricing/page.tsx`

**Purpose:** Display subscription plans and pricing information

**Key Features:**

- 3 tiers: Starter (Free), Pro ($29), Enterprise (Custom)
- Feature comparison per tier
- Limitations display
- CTA buttons with plan selection
- Comprehensive FAQ section
- Plan highlights for Pro tier
- Responsive card grid layout

**Technologies:**

- Conditional rendering for highlighted plans
- Smooth hover animations
- Responsive grid layout

### 5. Docs Hub

**Location:** `/app/docs/page.tsx`

**Purpose:** Documentation landing page with organized sections and search

**Key Features:**

- 6 major doc sections (Getting Started, Web Interface, API, CLI, Integrations, Advanced)
- Full-text search integration
- Popular search suggestions
- Getting help section with Discord/GitHub/Email
- Responsive section grid

**Technologies:**

- Form handling for search
- Dynamic routing to search results

### 6. FAQ Page

**Location:** `/app/faq/page.tsx`

**Purpose:** Frequently asked questions organized by category

**Key Features:**

- 6 categories (Getting Started, Features, Pricing, API, Data & Privacy, Support)
- Expandable Q&A items
- Smooth collapse animations
- Contact links
- Category grouping and visual separation

**Technologies:**

- React useState for expansion state
- Smooth transitions and transforms

## Supporting Components

### MDXContent

**Location:** `/components/MDXContent.tsx`

Renders markdown with full styling and syntax highlighting:

- Custom heading styles (h1-h4)
- Paragraph and list styling
- Blockquote styling
- Code block syntax highlighting (10+ languages)
- Table rendering
- Link styling with target="\_blank"

### ShareButton

**Location:** `/components/ShareButton.tsx`

Provides sharing functionality:

- Native Web Share API fallback
- Copy-to-clipboard for unsupported browsers
- Visual feedback (✓ Copied)
- URL generation

### ExportButton

**Location:** `/components/ExportButton.tsx`

Export reports in multiple formats:

- PDF via browser print dialog
- Markdown file download
- Dropdown menu with options
- Disabled state handling

### Navigation

**Location:** `/components/Navigation.tsx`

Responsive header navigation:

- Desktop and mobile views
- Authentication state handling
- Active route highlighting
- Mobile hamburger menu
- User menu with logout

## Supporting Hooks

### useSSE

**Location:** `/hooks/useSSE.ts`

Server-Sent Events hook for real-time updates:

- Auto-connect to job SSE endpoint
- Event type parsing (message, agent_update, job_complete)
- Auto-reconnect on failure
- Error handling and state management
- Token-based authentication

## Architecture Notes

### Authentication Flow

All protected pages check `useAuthStore` for `isAuthenticated()` and redirect to login if needed.

### GraphQL Integration

Pages use Apollo Client with:

- Query hooks for data fetching
- Mutation hooks for updates
- Poll intervals for status updates
- Error handling with user feedback

### Real-time Updates

Live page uses dual approach:

1. **Primary:** Server-Sent Events (SSE) for streaming updates
2. **Fallback:** GraphQL polling every 2 seconds

### Responsive Design

- Mobile-first approach
- Tailwind CSS grid layouts
- Responsive typography
- Mobile navigation menus
- Touch-friendly buttons

### Error Handling

- Network error recovery with SSE reconnect
- GraphQL error messages to users
- Fallback UI states
- Form validation with feedback

## Key Design Patterns

### Real-time Status

```
Agent Status → Color Indicator → Progress Bar → Duration Display
```

### Report Display

```
Header → Metadata → Summary → Content (Rich MD) → Sources → Footer
```

### Form Interaction

```
Input → Validation → Mutation → Feedback → Refetch Data
```

### Authentication

```
Check Auth → Redirect if needed → Render Protected Component
```

## Performance Optimizations

1. **Code Splitting:** Each page is a separate bundle
2. **SSE Efficiency:** Minimal polling with event-driven updates
3. **Apollo Caching:** Automatic response caching
4. **Image Optimization:** Next.js Image component (where used)
5. **Component Memoization:** Prevent unnecessary re-renders

## Browser Compatibility

- Chrome/Edge 90+
- Firefox 88+
- Safari 14+
- Mobile browsers (iOS Safari, Chrome Mobile)

## Dependencies

Key package requirements:

- `next` - Framework
- `react` - UI
- `@apollo/client` - GraphQL
- `tailwindcss` - Styling
- `react-markdown` - Markdown rendering
- `react-syntax-highlighter` - Code highlighting

## Environment Variables (Frontend)

```
NEXT_PUBLIC_API_URL=http://localhost:4000
NEXT_PUBLIC_GRAPHQL_ENDPOINT=http://localhost:4000/graphql
```

## Future Enhancements

1. **Team Collaboration:** Multiple users per workspace
2. **Advanced Search:** Full-text search in docs and past reports
3. **Custom Branding:** White-label options for Enterprise
4. **Analytics:** Usage tracking and insights
5. **Scheduled Research:** Recurring jobs on schedule
6. **Integrations:** Slack, Zapier, GPT Plugins
7. **Templates:** Pre-built research templates
8. **Social Sharing:** Custom cards for Twitter/LinkedIn

## Testing Recommendations

1. Unit tests for utility functions
2. Component tests for interactive elements
3. E2E tests for critical user flows
4. Visual regression tests for responsive design
5. Performance tests for real-time updates

## Deployment Checklist

- [ ] Environment variables configured
- [ ] GraphQL endpoint accessible
- [ ] SSE endpoint functional
- [ ] Authentication tokens valid
- [ ] API keys configured
- [ ] Database migrations complete
- [ ] CDN/Static assets optimized
- [ ] SSL certificates valid
