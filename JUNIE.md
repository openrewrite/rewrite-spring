# Junie Project Guidelines

## What is Junie?

Junie is a workflow automation tool from JetBrains used in this project for automating certain development and maintenance tasks. It integrates with GitHub Actions to streamline issue management and code maintenance processes.

## How Junie is Used in This Project

In the rewrite-spring project, Junie is used to:

1. Automate issue handling through GitHub workflows
2. Facilitate code maintenance tasks
3. Streamline pull request processes

## Setup and Configuration

### Prerequisites

- GitHub repository with appropriate permissions
- Junie runner configured (junie-runner.jar)
- GitHub Actions enabled

### Workflow Configuration

The Junie workflow is configured in `.github/workflows/junie.yml`. This workflow:

- Is triggered manually via workflow_dispatch
- Requires a run ID and workflow parameters
- Uses the JetBrains Junie workflow template

## Guidelines for Contributors

### Using Junie for Issue Management

1. Issues should be properly formatted and tagged to work with Junie automation
2. When creating issues that require Junie automation, include all necessary information in the issue description
3. Follow the standard issue template when applicable

### Running Junie Workflows

To run a Junie workflow manually:

1. Go to the Actions tab in the GitHub repository
2. Select the Junie workflow
3. Click "Run workflow"
4. Provide the required run ID and workflow parameters
5. Submit the workflow run

### Troubleshooting

If you encounter issues with Junie workflows:

1. Check the workflow run logs for error messages
2. Verify that all required parameters were provided
3. Ensure you have the necessary permissions
4. Check that the junie-runner.jar is up to date

## Best Practices

1. Keep the Junie configuration up to date
2. Document any changes to Junie workflows
3. Test workflow changes in a development environment before applying to production
4. Use descriptive run IDs and parameters for better tracking

## Resources

- [JetBrains Junie Documentation](https://www.jetbrains.com/help/space/automation.html)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)