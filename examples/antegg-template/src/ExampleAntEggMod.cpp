// Minimal .AntEgg native mod.
//
// This is a preload-native mod in the same ABI/loading style as examples/full-cpp-mod, packaged
// in an .AntEgg instead of a .levipack. The launcher extracts bin/libexample.so and passes its
// absolute path to the preloader's native-mod injection entry point; nothing about this file
// changes because of the packaging.

#include <pl/Mod.hpp>

#include <string>

namespace {

class ExampleAntEggMod : public ll::mod::NativeMod {
 public:
  bool onLoad() override {
    getSelf().getLogger().info("Example .AntEgg native mod loaded");
    return true;
  }

  bool onUnload() override { return true; }
};

}  // namespace

PL_REGISTER_MOD(ExampleAntEggMod, "example-antegg-mod", "1.0.0");
