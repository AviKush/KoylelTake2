# Koylel App Development Roadmap

This document outlines the planned development tasks and feature backlog for the Koylel app.

## Short-term Goals (1-3 months)

### Architecture Improvements
- [ ] Refactor MainActivity to reduce its size and complexity
- [ ] Implement MVVM architecture with ViewModels
- [ ] Create data repositories for cleaner data access
- [ ] Add unit tests for core functionality

### Performance Enhancements
- [ ] Optimize location monitoring for better battery life
- [ ] Improve app startup time
- [ ] Reduce memory usage in long-running services

### UI Improvements
- [ ] Migrate more UI components to Jetpack Compose
- [ ] Improve accessibility features
- [ ] Add dark mode support
- [ ] Create more intuitive location management UI

## Medium-term Goals (3-6 months)

### Feature Additions
- [ ] Add statistics dashboard for study time analysis
- [ ] Implement calendar view for historical data
- [ ] Create widget for quick time logging
- [ ] Add support for study categories/subjects

### Data Management
- [ ] Migrate from SharedPreferences to Room database
- [ ] Implement data export in multiple formats (CSV, PDF)
- [ ] Add automatic backup options

### User Experience
- [ ] Add onboarding flow for new users
- [ ] Implement more customization options
- [ ] Create preset themes

## Long-term Goals (6+ months)

### Advanced Features
- [ ] Optional cloud synchronization
- [ ] Multi-device support
- [ ] Social features (optional sharing with study partners)
- [ ] Integration with calendar apps

### Platform Expansion
- [ ] Wear OS companion app
- [ ] Web dashboard for data visualization
- [ ] iOS version consideration

## Technical Debt
- [ ] Refactor large classes into smaller, focused components
- [ ] Improve error handling and recovery
- [ ] Add comprehensive logging for troubleshooting
- [ ] Increase test coverage

## Known Issues
- [ ] Location detection occasionally fails in certain environments
- [ ] Hebrew text rendering issues on some devices
- [ ] High battery usage during continuous location monitoring
- [ ] UI performance issues with large datasets

## Community Requests
- [ ] Add support for multiple Yeshivas/learning locations
- [ ] Implement study group features
- [ ] Create export format compatible with common reporting tools
- [ ] Add voice commands for hands-free logging 