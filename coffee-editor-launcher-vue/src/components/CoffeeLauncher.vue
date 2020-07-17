<template>
  <div class="coffee">
    <div>
      <div class="disc">
        <p>
          The coffee editor demo is for demonstration purposes only and will
          <b>time out after 30 minutes</b>.
        </p>
        <p>
          By launching the coffee editor, I agree to the terms, conditions and
          privacy policy below (click to expand).
        </p>
      </div>
      <div class="launch">
        <v-btn
          rounded
          color="#11b3bb"
          dark
          :loading="launching"
          :disabled="launching"
          @click="launch"
          >{{ buttonText }}</v-btn
        >
      </div>
    </div>
    <div class="loading">
      <p>{{ loadingText }}</p>
    </div>
  </div>
</template>

<script lang="ts">
import { Component, Prop, Vue } from "vue-property-decorator";
import axios from "axios";

@Component
export default class CoffeeLauncher extends Vue {
  @Prop() private url!: string;

  private loadingText = " ";
  private buttonText = "Launch the Coffee Editor";
  private launching = false;

  private async launch() {
    this.launching = true;
    this.loadingText =
      "Your Coffee Editor demo instance is being launched. This can take up to a minute. You will be redirected once finished.";
    setTimeout(() => {
      if (this.launching) {
        this.loadingText =
          "Launching is taking longer than usual because there are many users at the moment. You will be redirected once finished.";
      }
    }, 45000);
    await axios
      .get(this.url)
      .then(response => {
        // handle success
        const coffeeEditorURL = response.data;
        location.replace(coffeeEditorURL);
      })
      .catch(error => {
        if (error.response !== undefined) {
          alert(error.response.data);
        } else {
          alert("There was a problem while launching the editor");
        }
      });
    this.launching = false;
    this.loadingText = " ";
  }
}
</script>

<style scoped>
.coffee {
  margin: 40px 0 0;
}
.loading {
  height: 20px;
  margin: 40px 0 0;
}
.launch button {
  margin-top: 40px;
}
</style>
