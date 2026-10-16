import { runE2eCompose } from './compose-project';

export default function globalTeardown(): void {
  runE2eCompose(['down', '--volumes', '--remove-orphans']);
}
