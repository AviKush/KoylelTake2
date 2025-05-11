# Contributing to Koylel

Thank you for your interest in contributing to the Koylel app! This document provides guidelines for contributing code, creating branches, submitting pull requests, and following code style conventions.

## Getting Started

1. Fork the repository
2. Clone your fork: `git clone https://github.com/yourusername/KoylelTake2.git`
3. Set up the development environment

## Development Environment Setup

1. Install Android Studio (latest stable version recommended)
2. Open the project in Android Studio
3. Sync Gradle files
4. Ensure you can build and run the app on an emulator or physical device

## Branching Strategy

We follow a simplified Git flow model:

- `main` - Production-ready code
- `develop` - Integration branch for features
- `feature/*` - New features (e.g., `feature/calendar-view`)
- `bugfix/*` - Bug fixes (e.g., `bugfix/location-crash`)
- `release/*` - Release preparation

### Creating a New Branch

```bash
# For a new feature
git checkout develop
git pull
git checkout -b feature/your-feature-name

# For a bug fix
git checkout develop
git pull
git checkout -b bugfix/issue-description
```

## Code Style Guidelines

### Kotlin

- Follow the [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Add comments for complex logic
- Keep functions small and focused on a single responsibility
- Use extension functions where appropriate

### XML

- Use descriptive IDs for views
- Group related attributes
- Follow consistent naming conventions for resources

### General

- Maximum line length: 100 characters
- Use 4 spaces for indentation (not tabs)
- Organize imports
- Remove unused code and imports

## Pull Request Process

1. Ensure your code follows the style guidelines
2. Update documentation if necessary
3. Test your changes thoroughly
4. Create a pull request against the `develop` branch
5. Provide a clear description of the changes
6. Reference any related issues

### PR Description Template

```
## Description
Brief description of the changes

## Related Issues
Fixes #IssueNumber

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## Testing
Describe how you tested your changes

## Screenshots (if applicable)
Add screenshots here
```

## Commit Message Guidelines

- Use the present tense ("Add feature" not "Added feature")
- Use the imperative mood ("Move cursor to..." not "Moves cursor to...")
- Limit the first line to 72 characters or less
- Reference issues and pull requests after the first line

Example:
```
Add geofence deletion functionality

Implements the ability to delete existing geofences from the UI.
Fixes #42
```

## Code Review Process

- All submissions require review
- Changes must pass automated tests
- Address all review comments before merging
- Maintainers may request changes before accepting

## Reporting Bugs

- Use the issue tracker
- Describe the bug in detail
- Include steps to reproduce
- Mention device/OS version and app version

Thank you for contributing to Koylel! 