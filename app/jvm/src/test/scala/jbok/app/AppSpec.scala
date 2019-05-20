package jbok.app

import cats.effect.IO
import distage.Locator
import jbok.core.CoreSpec

trait AppSpec extends CoreSpec {
  override val locator: IO[Locator] =
    AppModule.withConfig[IO](config).allocated.map(_._1)
}