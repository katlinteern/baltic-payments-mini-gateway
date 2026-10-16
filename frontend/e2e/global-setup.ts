import { runE2eCompose, waitForE2eStack } from './compose-project';

export default async function globalSetup(): Promise<void> {
  runE2eCompose(['up', '--build', '--detach']);
  try {
    await waitForE2eStack();
  } catch (error) {
    runE2eCompose(['down', '--volumes', '--remove-orphans']);
    throw error;
  }
}
